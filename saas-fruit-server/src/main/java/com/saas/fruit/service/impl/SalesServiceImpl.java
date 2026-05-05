package com.saas.fruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.fruit.common.BusinessException;
import com.saas.fruit.dto.request.SalesOrderCreateRequest;
import com.saas.fruit.dto.request.SalesOrderCreateRequest.OrderItemRequest;
import com.saas.fruit.entity.Customer;
import com.saas.fruit.entity.FinancialLedger;
import com.saas.fruit.entity.Inventory;
import com.saas.fruit.entity.SalesOrder;
import com.saas.fruit.entity.SalesOrderItem;
import com.saas.fruit.mapper.CustomerMapper;
import com.saas.fruit.mapper.FinancialLedgerMapper;
import com.saas.fruit.mapper.InventoryMapper;
import com.saas.fruit.mapper.SalesOrderItemMapper;
import com.saas.fruit.mapper.SalesOrderMapper;
import com.saas.fruit.service.SalesService;
import com.saas.fruit.utils.BatchNoGenerator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售单服务实现类
 * 
 * 严格按照 JS 云函数 sales_order_create 的逻辑翻译
 * 
 * 核心要点：
 * - 金额统一以"分"（Integer）为单位存储，避免浮点数精度问题
 * - 全程使用 @Transactional + SELECT ... FOR UPDATE 行级锁保证数据一致性
 * - FIFO（先进先出）扣减批次库存，按 inboundTime 排序
 * - 成本计算使用 Math.floor（Java 中 BigDecimal.setScale(0, FLOOR)）
 * - batch_list 字段：MySQL VARCHAR/JSON 类型，使用 Jackson 序列化/反序列化
 * - 财务流水：先全额应收（ledgerType=receivable, transactionType=sale_debt）
 *             再收款核销（ledgerType=receivable, transactionType=payment_received）
 */
@Slf4j
@Service
public class SalesServiceImpl implements SalesService {

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private FinancialLedgerMapper financialLedgerMapper;

    @Autowired
    private BatchNoGenerator batchNoGenerator;

    /** Jackson ObjectMapper，用于 batch_list JSON 反序列化/序列化 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 默认租户ID（二期 SaaS 化时从上下文获取） */
    private static final String DEFAULT_TENANT_ID = "default";

    /**
     * 创建销售订单（销售开单）
     * 
     * @Transactional 确保所有操作在同一事务中执行
     * 使用 selectByIdForUpdate 行级锁防止并发超卖
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder create(SalesOrderCreateRequest req) {
        // ==================== 第一步：入参校验 ====================
        // 对应 JS 第186-221行

        // 校验客户ID
        if (req.getCustomerId() == null) {
            throw new BusinessException(400, "缺少必要参数：customerId");
        }

        // 校验销售类型
        String saleType = req.getSaleType() != null ? req.getSaleType() : "wholesale";
        if (!"wholesale".equals(saleType) && !"retail".equals(saleType)) {
            throw new BusinessException(400, "销售类型 saleType 必须是 wholesale 或 retail");
        }

        // 校验订单明细
        List<OrderItemRequest> orderItems = req.getOrderItems();
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException(400, "订单明细 orderItems 不能为空");
        }

        // 校验每个订单项
        for (int i = 0; i < orderItems.size(); i++) {
            OrderItemRequest item = orderItems.get(i);
            if (item.getInventoryId() == null) {
                throw new BusinessException(400, "第" + (i + 1) + "个订单项缺少 inventoryId");
            }
            if (item.getWeightJin() == null || item.getWeightJin().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "第" + (i + 1) + "个订单项的 weightJin 必须大于0");
            }
            if (item.getUnitPriceFen() == null || item.getUnitPriceFen() < 0) {
                throw new BusinessException(400, "第" + (i + 1) + "个订单项的 unitPriceFen 无效");
            }
        }

        int paidAmountFen = req.getPaidAmountFen() != null ? req.getPaidAmountFen() : 0;
        if (paidAmountFen < 0) {
            throw new BusinessException(400, "实收金额 paidAmountFen 不能为负数");
        }

        long currentTime = System.currentTimeMillis();
        String tenantId = DEFAULT_TENANT_ID;

        // ==================== 第二步：查询客户信息 ====================
        // 对应 JS 第228-256行
        Customer customer = customerMapper.selectById(req.getCustomerId());
        if (customer == null) {
            throw new BusinessException(404, "客户不存在：" + req.getCustomerId());
        }

        // 校验客户状态（对应 JS 第250-256行）
        if ("inactive".equals(customer.getStatus())) {
            throw new BusinessException(403, "该客户已停用，无法开单");
        }
        if ("blacklist".equals(customer.getStatus())) {
            throw new BusinessException(403, "该客户已被列入黑名单，无法开单");
        }

        // ==================== 第三步：行级锁查询库存并校验 ====================
        // 对应 JS 第258-298行
        // 
        // JS 中 MongoDB 使用 dbCmd.in(inventoryIds) 批量查询
        // Java 中 MySQL 使用 selectByIdForUpdate（SELECT ... FOR UPDATE）逐个加行级锁
        // 确保在当前事务中这些行不会被其他事务修改，防止并发超卖

        Map<Long, Inventory> inventoryMap = new HashMap<>();
        for (OrderItemRequest item : orderItems) {
            // 使用行级锁查询库存（对应 JS 第262-263行，但加了锁）
            Inventory inventory = inventoryMapper.selectByIdForUpdate(item.getInventoryId());

            if (inventory == null) {
                throw new BusinessException(400, "库存记录不存在：" + item.getInventoryId());
            }

            // 校验库存充足（对应 JS 第289-298行）
            BigDecimal currentStock = inventory.getCurrentStockJin() != null
                    ? inventory.getCurrentStockJin() : BigDecimal.ZERO;
            BigDecimal requiredWeight = item.getWeightJin();

            if (currentStock.compareTo(requiredWeight) < 0) {
                throw new BusinessException(400,
                        "商品【" + inventory.getProductName() + "】库存不足，"
                                + "当前库存 " + currentStock.stripTrailingZeros().toPlainString() + " 斤，"
                                + "需要 " + requiredWeight.stripTrailingZeros().toPlainString() + " 斤");
            }

            inventoryMap.put(item.getInventoryId(), inventory);
        }

        // ==================== 第四步：FIFO 扣减批次库存 ====================
        // 对应 JS 第300-371行（含 fifoDeductBatches 函数第100-171行）

        List<ProcessedOrderItem> processedOrderItems = new ArrayList<>();
        int totalCostFen = 0;
        int totalAmountFen = 0;
        BigDecimal totalWeightJin = BigDecimal.ZERO;

        for (OrderItemRequest item : orderItems) {
            Inventory inventory = inventoryMap.get(item.getInventoryId());
            BigDecimal weightJin = item.getWeightJin();
            int unitPriceFen = item.getUnitPriceFen();

            // 计算小计金额（对应 JS 第312行：Math.floor(weightJin * unitPriceFen)）
            int subtotalFen = calcFloorAmount(weightJin, unitPriceFen);

            // 解析 batch_list JSON 字符串为批次对象列表
            List<BatchEntry> batchList = parseBatchList(inventory.getBatchList());

            // 执行 FIFO 扣减（核心逻辑，对应 JS fifoDeductBatches 函数）
            FifoResult fifoResult = fifoDeductBatches(batchList, weightJin);

            // 累加总计
            totalCostFen += fifoResult.costTotalFen;
            totalAmountFen += subtotalFen;
            totalWeightJin = totalWeightJin.add(weightJin);

            // 计算加权平均成本单价（对应 JS 第321-323行）
            int avgCostPriceFen = 0;
            if (weightJin.compareTo(BigDecimal.ZERO) > 0) {
                avgCostPriceFen = new BigDecimal(fifoResult.costTotalFen)
                        .divide(weightJin, 0, RoundingMode.FLOOR).intValue();
            }

            // 取第一个扣减批次的信息作为主批次（对应 JS 第325行）
            DeductedBatch primaryBatch = fifoResult.deductedBatches.isEmpty()
                    ? new DeductedBatch("", "", BigDecimal.ZERO, 0, 0)
                    : fifoResult.deductedBatches.get(0);

            // 构建处理后的订单项（对应 JS 第327-341行）
            ProcessedOrderItem processed = new ProcessedOrderItem();
            processed.inventoryId = item.getInventoryId();
            processed.productName = inventory.getProductName();
            processed.grade = inventory.getGrade();
            processed.spec = inventory.getSpec();
            processed.weightJin = weightJin;
            processed.unitPriceFen = unitPriceFen;
            processed.subtotalFen = subtotalFen;
            processed.costPriceFen = avgCostPriceFen;
            processed.costTotalFen = fifoResult.costTotalFen;
            processed.profitFen = subtotalFen - fifoResult.costTotalFen;
            processed.batchId = primaryBatch.batchId;
            processed.batchNo = primaryBatch.batchNo;
            processed.deductedBatches = fifoResult.deductedBatches;
            processed.newBatchList = fifoResult.newBatchList;

            processedOrderItems.add(processed);
        }

        // ==================== 第五步：计算欠款和收款状态 ====================
        // 对应 JS 第373-381行
        int debtAmountFen = totalAmountFen - paidAmountFen;

        String paymentStatus;
        if (paidAmountFen >= totalAmountFen) {
            paymentStatus = "paid";
        } else if (paidAmountFen > 0) {
            paymentStatus = "partial";
        } else {
            paymentStatus = "unpaid";
        }

        int totalProfitFen = totalAmountFen - totalCostFen;

        // ==================== 第六步：生成销售单号并创建销售订单 ====================
        // 对应 JS 第383-415行

        // 查询当天最大序号，生成销售单号
        long seq = getTodaySalesOrderMaxSeq();
        String orderNo = batchNoGenerator.genSalesOrderNo(seq);

        // 构建销售订单实体（对应 JS 第386-411行）
        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setTenantId(tenantId);
        salesOrder.setOrderNo(orderNo);
        salesOrder.setCustomerId(req.getCustomerId());
        salesOrder.setCustomerName(customer.getName());
        salesOrder.setCustomerPhone(customer.getPhone() != null ? customer.getPhone() : "");
        salesOrder.setSaleType(saleType);
        salesOrder.setTotalWeightJin(totalWeightJin);
        salesOrder.setTotalAmountFen(totalAmountFen);
        salesOrder.setTotalCostFen(totalCostFen);
        salesOrder.setTotalProfitFen(totalProfitFen);
        salesOrder.setPaymentStatus(paymentStatus);
        salesOrder.setPaidAmountFen(paidAmountFen);
        salesOrder.setDebtAmountFen(debtAmountFen);
        salesOrder.setSaleDate(currentTime);
        salesOrder.setStatus("completed");
        salesOrder.setRemark("");
        salesOrder.setCreateTime(currentTime);
        salesOrder.setUpdateTime(currentTime);
        salesOrder.setOperatorId(req.getOperatorId() != null ? req.getOperatorId() : "");
        salesOrder.setOperatorName(req.getOperatorName() != null ? req.getOperatorName() : "");

        // 插入销售订单（对应 JS 第413-414行）
        int insertOrderResult = salesOrderMapper.insert(salesOrder);
        if (insertOrderResult <= 0) {
            throw new BusinessException(500, "销售订单插入失败");
        }

        // 插入销售订单明细（对应 JS 中没有单独的 order_items 表插入，
        // 但 data model 设计要求拆分为 sales_orders + sales_order_items 两张表）
        for (int i = 0; i < processedOrderItems.size(); i++) {
            ProcessedOrderItem processed = processedOrderItems.get(i);

            SalesOrderItem orderItem = new SalesOrderItem();
            orderItem.setOrderId(salesOrder.getId());
            orderItem.setInventoryId(processed.inventoryId);
            orderItem.setProductName(processed.productName);
            orderItem.setGrade(processed.grade != null ? processed.grade : "");
            orderItem.setSpec(processed.spec != null ? processed.spec : "");
            orderItem.setWeightJin(processed.weightJin);
            orderItem.setUnitPriceFen(processed.unitPriceFen);
            orderItem.setSubtotalFen(processed.subtotalFen);
            orderItem.setCostPriceFen(processed.costPriceFen);
            orderItem.setCostTotalFen(processed.costTotalFen);
            orderItem.setProfitFen(processed.profitFen);
            orderItem.setBatchId(processed.batchId);
            orderItem.setBatchNo(processed.batchNo);
            orderItem.setSortOrder(i + 1);

            salesOrderItemMapper.insert(orderItem);
        }

        // ==================== 第七步：更新库存记录 ====================
        // 对应 JS 第417-423行
        for (ProcessedOrderItem processed : processedOrderItems) {
            Inventory inventory = inventoryMap.get(processed.inventoryId);

            // 计算新的库存重量（对应 JS 第343行）
            BigDecimal newStockJin = inventory.getCurrentStockJin().subtract(processed.weightJin);

            // 计算新的累计出库重量（对应 JS 第344行）
            BigDecimal newTotalOutboundJin = (inventory.getTotalOutboundJin() != null
                    ? inventory.getTotalOutboundJin() : BigDecimal.ZERO).add(processed.weightJin);

            // 计算新的库存总成本（对应 JS 第345行）
            int newTotalCostFen = (inventory.getTotalCostFen() != null ? inventory.getTotalCostFen() : 0)
                    - processed.costTotalFen;

            // 计算新的加权平均成本（对应 JS 第347-349行）
            int newUnitCostFen = 0;
            if (newStockJin.compareTo(BigDecimal.ZERO) > 0 && newTotalCostFen > 0) {
                newUnitCostFen = new BigDecimal(newTotalCostFen)
                        .divide(newStockJin, 0, RoundingMode.FLOOR).intValue();
            }

            // 判断库存状态（对应 JS 第351-356行）
            String newStatus;
            if (newStockJin.compareTo(BigDecimal.ZERO) <= 0) {
                newStatus = "out_of_stock";
            } else if (inventory.getWarningStockJin() != null
                    && newStockJin.compareTo(inventory.getWarningStockJin()) <= 0) {
                newStatus = "low_stock";
            } else {
                newStatus = "normal";
            }

            // 将新的 batchList 序列化为 JSON 字符串
            String newBatchListJson;
            try {
                newBatchListJson = OBJECT_MAPPER.writeValueAsString(processed.newBatchList);
            } catch (JsonProcessingException e) {
                log.error("batch_list JSON 序列化失败, inventoryId={}", processed.inventoryId, e);
                throw new BusinessException(500, "库存批次序列化失败");
            }

            // 构建更新对象（对应 JS 第360-370行）
            Inventory updateInventory = new Inventory();
            updateInventory.setId(processed.inventoryId);
            updateInventory.setCurrentStockJin(newStockJin);
            updateInventory.setUnitCostFen(newUnitCostFen);
            updateInventory.setTotalCostFen(newTotalCostFen);
            updateInventory.setTotalOutboundJin(newTotalOutboundJin);
            updateInventory.setLastOutboundTime(currentTime);
            updateInventory.setStatus(newStatus);
            updateInventory.setUpdateTime(currentTime);
            updateInventory.setBatchList(newBatchListJson);

            inventoryMapper.updateById(updateInventory);
        }

        // ==================== 第八步：更新客户统计信息 ====================
        // 对应 JS 第425-444行
        customerMapper.updateSalesStats(
                req.getCustomerId(),
                totalAmountFen,       // totalSalesFen
                paidAmountFen,        // totalPaidFen
                debtAmountFen,        // totalDebtFen
                currentTime,          // lastOrderTime
                currentTime           // updateTime
        );

        // ==================== 第九步：生成财务流水 ====================
        // 对应 JS 第446-515行
        // 核心原则：先产生全额应收，再核销收款
        //  事件A：产生全额应收（卖了货，账面应收款增加）
        //  事件B：发生收款核销（客户付款，账面应收款减少）

        int currentBalanceFen = customer.getTotalDebtFen() != null ? customer.getTotalDebtFen() : 0;

        // 9.1 生成"全额应收"的销售欠款流水（只要总金额 > 0）
        // 对应 JS 第450-478行
        if (totalAmountFen > 0) {
            long ledgerSeq = getTodayReceivableLedgerMaxSeq();
            String ledgerNoDebt = batchNoGenerator.genReceiveLedgerNo(ledgerSeq);
            int balanceAfterDebtFen = currentBalanceFen + totalAmountFen;

            FinancialLedger debtLedger = new FinancialLedger();
            debtLedger.setTenantId(tenantId);
            debtLedger.setLedgerNo(ledgerNoDebt);
            debtLedger.setLedgerType("receivable");         // 应收账款
            debtLedger.setTransactionType("sale_debt");
            debtLedger.setAmountFen(totalAmountFen);         // 全额
            debtLedger.setBalanceBeforeFen(currentBalanceFen);
            debtLedger.setBalanceAfterFen(balanceAfterDebtFen);
            debtLedger.setRelatedPartyType("customer");
            debtLedger.setRelatedPartyId(req.getCustomerId());
            debtLedger.setRelatedPartyName(customer.getName());
            debtLedger.setRelatedOrderType("sales_order");
            debtLedger.setRelatedOrderId(salesOrder.getId());
            debtLedger.setRelatedOrderNo(orderNo);
            debtLedger.setTransactionTime(currentTime);
            debtLedger.setRemark("销售单 " + orderNo + " 产生应收");
            debtLedger.setCreateTime(currentTime);
            debtLedger.setOperatorId(req.getOperatorId() != null ? req.getOperatorId() : "");
            debtLedger.setOperatorName(req.getOperatorName() != null ? req.getOperatorName() : "");

            financialLedgerMapper.insert(debtLedger);
            currentBalanceFen = balanceAfterDebtFen; // 更新当前余额指针
        }

        // 9.2 有实收金额时生成收款核销流水
        // 对应 JS 第481-515行
        if (paidAmountFen > 0) {
            long ledgerSeq = getTodayReceivableLedgerMaxSeq();
            String ledgerNoPayment = batchNoGenerator.genReceiveLedgerNo(ledgerSeq);
            int balanceAfterPaymentFen = currentBalanceFen - paidAmountFen; // 收款减少应收

            // 构建核销明细（对应 JS 第499-505行）
            List<Map<String, Object>> writeOffDetailsList = new ArrayList<>();
            Map<String, Object> writeOffDetail = new HashMap<>();
            writeOffDetail.put("orderId", salesOrder.getId());
            writeOffDetail.put("orderNo", orderNo);
            writeOffDetail.put("amountFen", paidAmountFen);
            writeOffDetail.put("orderDebtBeforeFen", totalAmountFen);
            writeOffDetail.put("orderDebtAfterFen", debtAmountFen); // 剩余欠款
            writeOffDetailsList.add(writeOffDetail);

            String writeOffDetailsJson;
            try {
                writeOffDetailsJson = OBJECT_MAPPER.writeValueAsString(writeOffDetailsList);
            } catch (JsonProcessingException e) {
                log.error("核销明细 JSON 序列化失败", e);
                throw new BusinessException(500, "财务流水生成失败：核销明细序列化异常");
            }

            FinancialLedger paymentLedger = new FinancialLedger();
            paymentLedger.setTenantId(tenantId);
            paymentLedger.setLedgerNo(ledgerNoPayment);
            paymentLedger.setLedgerType("receivable");       // 应收账款
            paymentLedger.setTransactionType("payment_received");
            paymentLedger.setAmountFen(paidAmountFen);
            paymentLedger.setBalanceBeforeFen(currentBalanceFen);
            paymentLedger.setBalanceAfterFen(balanceAfterPaymentFen);
            paymentLedger.setRelatedPartyType("customer");
            paymentLedger.setRelatedPartyId(req.getCustomerId());
            paymentLedger.setRelatedPartyName(customer.getName());
            paymentLedger.setRelatedOrderType("sales_order");
            paymentLedger.setRelatedOrderId(salesOrder.getId());
            paymentLedger.setRelatedOrderNo(orderNo);
            paymentLedger.setWriteOffDetails(writeOffDetailsJson);
            paymentLedger.setTransactionTime(currentTime + 1); // 稍微错开1毫秒保证排序
            paymentLedger.setRemark("销售单 " + orderNo + " 收款");
            paymentLedger.setCreateTime(currentTime);
            paymentLedger.setOperatorId(req.getOperatorId() != null ? req.getOperatorId() : "");
            paymentLedger.setOperatorName(req.getOperatorName() != null ? req.getOperatorName() : "");

            financialLedgerMapper.insert(paymentLedger);
        }

        log.info("销售开单成功：订单号={}, 客户={}, 总额={}分, 利润={}分",
                orderNo, customer.getName(), totalAmountFen, totalProfitFen);

        return salesOrder;
    }

    // ==================== FIFO 核心算法 ====================

    /**
     * FIFO（先进先出）扣减批次库存
     * 
     * 严格按照 JS 云函数 fifoDeductBatches 函数（第100-171行）翻译
     * 
     * 算法流程：
     * 1. 将批次列表按 inboundTime 升序排列（最早入库的批次优先扣减）
     * 2. 遍历排序后的批次，按顺序扣减：
     *    - 当前批次库存充足（>= 剩余需求重量）：只扣部分，批次仍有剩余
     *    - 当前批次库存不足（< 剩余需求重量）：全部扣完，继续下一个批次
     * 3. 每笔扣减的成本 = Math.floor(deductWeight * unitCostFen)
     * 4. 如果所有批次扣完仍不够，抛出"库存不足"异常
     * 
     * @param batchList   批次列表（已解析的 JSON）
     * @param requiredWeightJin 需要扣减的重量（斤）
     * @return FifoResult 包含总成本、扣减明细、新批次列表
     */
    private FifoResult fifoDeductBatches(List<BatchEntry> batchList, BigDecimal requiredWeightJin) {
        // 按入库时间升序排列（对应 JS 第101-103行）
        List<BatchEntry> sortedBatches = new ArrayList<>(batchList);
        sortedBatches.sort(Comparator.comparingLong(b -> b.inboundTime != null ? b.inboundTime : 0L));

        BigDecimal remainingWeight = requiredWeightJin;
        int costTotalFen = 0;
        List<DeductedBatch> deductedBatches = new ArrayList<>();
        List<BatchEntry> newBatchList = new ArrayList<>();

        for (BatchEntry batch : sortedBatches) {
            // 如果已满足需求重量，剩余批次原样保留（对应 JS 第111-114行）
            if (remainingWeight.compareTo(BigDecimal.ZERO) <= 0) {
                newBatchList.add(batch);
                continue;
            }

            BigDecimal batchStock = batch.stockJin != null ? batch.stockJin : BigDecimal.ZERO;
            int batchUnitCost = batch.unitCostFen != null ? batch.unitCostFen : 0;

            // 跳过空库存的批次（对应 JS 第119-121行）
            if (batchStock.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (batchStock.compareTo(remainingWeight) >= 0) {
                // 场景A：当前批次库存充足，只扣部分（对应 JS 第123-145行）
                BigDecimal deductWeight = remainingWeight;
                int deductCost = calcFloorAmount(deductWeight, batchUnitCost);

                costTotalFen += deductCost;
                deductedBatches.add(new DeductedBatch(
                        batch.batchId, batch.purchaseBatchNo, deductWeight, batchUnitCost, deductCost));

                // 批次扣减后还有剩余
                BigDecimal newBatchStock = batchStock.subtract(deductWeight);
                if (newBatchStock.compareTo(BigDecimal.ZERO) > 0) {
                    BatchEntry remainingBatch = new BatchEntry();
                    remainingBatch.batchId = batch.batchId;
                    remainingBatch.purchaseBatchNo = batch.purchaseBatchNo;
                    remainingBatch.inboundTime = batch.inboundTime;
                    remainingBatch.stockJin = newBatchStock;
                    remainingBatch.unitCostFen = batch.unitCostFen;
                    newBatchList.add(remainingBatch);
                }
                // newBatchStock <= 0 时该批次已耗尽，不加入新列表

                remainingWeight = BigDecimal.ZERO;
            } else {
                // 场景B：当前批次库存不足，全部扣完（对应 JS 第145-159行）
                BigDecimal deductWeight = batchStock;
                int deductCost = calcFloorAmount(deductWeight, batchUnitCost);

                costTotalFen += deductCost;
                deductedBatches.add(new DeductedBatch(
                        batch.batchId, batch.purchaseBatchNo, deductWeight, batchUnitCost, deductCost));

                remainingWeight = remainingWeight.subtract(deductWeight);
                // 该批次已耗尽，不加入 newBatchList
            }
        }

        // 如果还有剩余需求未满足，抛出异常（对应 JS 第162-164行）
        if (remainingWeight.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(400,
                    "库存不足，还差 " + remainingWeight.stripTrailingZeros().toPlainString() + " 斤");
        }

        return new FifoResult(costTotalFen, deductedBatches, newBatchList);
    }

    // ==================== JSON 解析辅助方法 ====================

    /**
     * 将 batch_list JSON 字符串解析为 BatchEntry 列表
     * 
     * 对应 JS 中直接使用 inventory.batch_list（MongoDB 自动反序列化）
     * MySQL 中 batch_list 存为 JSON 字符串，需要手动反序列化
     */
    private List<BatchEntry> parseBatchList(String batchListJson) {
        if (batchListJson == null || batchListJson.trim().isEmpty() || "[]".equals(batchListJson.trim())) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(batchListJson, new TypeReference<List<BatchEntry>>() {});
        } catch (JsonProcessingException e) {
            log.error("batch_list JSON 反序列化失败: {}", batchListJson, e);
            throw new BusinessException(500, "库存批次数据格式异常");
        }
    }

    // ==================== 序号查询辅助方法 ====================

    /**
     * 获取当天销售订单的最大序号 + 1
     * 
     * 对应 JS generateOrderNo 函数（第58-71行）
     * 格式：XS + yyyyMMdd + 4位序号
     */
    private long getTodaySalesOrderMaxSeq() {
        String todayDateStr = com.saas.fruit.utils.DateUtil.getDateStr();
        String prefix = "XS" + todayDateStr;

        QueryWrapper<SalesOrder> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("order_no", prefix);

        long count = salesOrderMapper.selectCount(queryWrapper);
        return count + 1;
    }

    /**
     * 获取当天应收（收款）流水号的最大序号 + 1
     * 
     * 对应 JS generateLedgerNo('receivable') 调用（第78-91行）
     * 格式：SK + yyyyMMdd + 4位序号
     */
    private long getTodayReceivableLedgerMaxSeq() {
        String todayDateStr = com.saas.fruit.utils.DateUtil.getDateStr();
        String prefix = "SK" + todayDateStr;

        QueryWrapper<FinancialLedger> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("ledger_no", prefix);

        long count = financialLedgerMapper.selectCount(queryWrapper);
        return count + 1;
    }

    // ==================== 金额计算工具方法 ====================

    /**
     * 计算"重量 x 单价"并向下取整
     * 
     * 对应 JS 中的 Math.floor(weight * unitPrice)
     * 
     * 使用 BigDecimal 保证计算精度，避免浮点数误差
     * 
     * @param weight      重量（斤）
     * @param unitPriceFen 单价（分/斤）
     * @return 计算结果（分），向下取整
     */
    private int calcFloorAmount(BigDecimal weight, int unitPriceFen) {
        if (weight == null || BigDecimal.ZERO.compareTo(weight) >= 0) {
            return 0;
        }
        return weight.multiply(new BigDecimal(unitPriceFen))
                .setScale(0, RoundingMode.FLOOR)
                .intValue();
    }

    // ==================== 内部数据类 ====================

    /**
     * 批次条目（对应 batch_list JSON 中每一项的结构）
     * 
     * batch_list JSON 示例：
     * [{
     *   "batch_id": "BATCH1234567890001",
     *   "purchase_batch_no": "CG202605010001",
     *   "inbound_time": 1714608000000,
     *   "stock_jin": 100.5,
     *   "unit_cost_fen": 500
     * }]
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class BatchEntry {
        /** 批次ID */
        private String batchId;
        /** 采购批次号 */
        private String purchaseBatchNo;
        /** 入库时间戳（用于排序） */
        private Long inboundTime;
        /** 该批次的当前库存重量（斤） */
        private BigDecimal stockJin;
        /** 该批次的单位成本（分/斤） */
        private Integer unitCostFen;
    }

    /**
     * 扣减批次明细（记录每个被扣减的批次的扣减信息）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DeductedBatch {
        /** 批次ID */
        private String batchId;
        /** 采购批次号 */
        private String batchNo;
        /** 扣减重量（斤） */
        private BigDecimal deductWeightJin;
        /** 单位成本（分/斤） */
        private int unitCostFen;
        /** 扣减成本（分） */
        private int costFen;
    }

    /**
     * FIFO 扣减结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FifoResult {
        /** 本次扣减的总成本（分） */
        private int costTotalFen;
        /** 每个批次的扣减明细 */
        private List<DeductedBatch> deductedBatches;
        /** 扣减后的新批次列表 */
        private List<BatchEntry> newBatchList;
    }

    /**
     * 处理后的订单项（内部使用，含 FIFO 中间结果）
     * 
     * 对应 JS 第327-341行的 processedOrderItems
     */
    @Data
    private static class ProcessedOrderItem {
        private Long inventoryId;
        private String productName;
        private String grade;
        private String spec;
        private BigDecimal weightJin;
        private int unitPriceFen;
        private int subtotalFen;
        private int costPriceFen;
        private int costTotalFen;
        private int profitFen;
        private String batchId;
        private String batchNo;
        /** FIFO 扣减明细（仅用于内部计算，不持久化到 order_items 表） */
        private List<DeductedBatch> deductedBatches;
        /** 扣减后的新批次列表（用于更新 inventory.batch_list） */
        private List<BatchEntry> newBatchList;
    }

    // ==================== 销售取消 ====================

    /**
     * 作废销售订单
     * 仅允许状态为 completed 且不是 paid 的订单作废
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        if (id == null) {
            throw new BusinessException(400, "缺少订单 ID 参数");
        }

        SalesOrder order = salesOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }

        if (!"completed".equals(order.getStatus())) {
            throw new BusinessException(400, "仅允许已完成状态的订单作废");
        }

        if ("paid".equals(order.getPaymentStatus())) {
            throw new BusinessException(400, "已全额收款的订单不允许作废");
        }

        SalesOrder updateOrder = new SalesOrder();
        updateOrder.setId(id);
        updateOrder.setStatus("cancelled");
        updateOrder.setUpdateTime(System.currentTimeMillis());
        salesOrderMapper.updateById(updateOrder);

        log.info("销售订单 {} (订单号: {}) 已作废", id, order.getOrderNo());
    }
}
