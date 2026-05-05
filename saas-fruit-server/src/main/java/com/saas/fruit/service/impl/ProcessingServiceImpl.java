package com.saas.fruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.fruit.common.BusinessException;
import com.saas.fruit.dto.request.ProcessingCreateRequest;
import com.saas.fruit.dto.request.ProcessingCreateRequest.OutputItem;
import com.saas.fruit.dto.request.ProcessingCreateRequest.ProcessingCosts;
import com.saas.fruit.entity.Inventory;
import com.saas.fruit.entity.ProcessingOrder;
import com.saas.fruit.entity.Purchase;
import com.saas.fruit.mapper.InventoryMapper;
import com.saas.fruit.mapper.ProcessingOrderMapper;
import com.saas.fruit.mapper.PurchaseMapper;
import com.saas.fruit.service.ProcessingService;
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
import java.util.List;

/**
 * 加工服务实现类
 * 
 * 严格按照 JS 云函数 process_and_stock_in 的逻辑翻译
 * 
 * 核心要点：
 * - 金额统一以"分"（Integer）为单位存储，避免浮点数精度问题
 * - 全程使用 @Transactional 保证数据一致性（替代 JS 中的 db.runTransaction）
 * - 成本按重量比例分摊到各产出品，最后一个用差额法补齐，避免分摊尾差
 * - 库存成本使用移动加权平均法更新
 * - batch_list 和 output_products 字段使用 Jackson ObjectMapper 处理 JSON
 */
@Slf4j
@Service
public class ProcessingServiceImpl implements ProcessingService {

    @Autowired
    private ProcessingOrderMapper processingOrderMapper;

    @Autowired
    private PurchaseMapper purchaseMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private BatchNoGenerator batchNoGenerator;

    /** Jackson ObjectMapper，用于 batch_list 和 output_products JSON 处理 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 默认租户ID（二期 SaaS 化时从上下文获取） */
    private static final String DEFAULT_TENANT_ID = "default";

    /**
     * 创建加工订单（加工入库）
     * 
     * @Transactional 确保所有数据库操作在同一事务中执行，失败时自动回滚
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessingOrder create(ProcessingCreateRequest req) {
        // ==================== 第一步：入参校验 ====================
        // 校验采购单ID
        if (req.getPurchaseId() == null) {
            throw new BusinessException(400, "缺少必要参数：purchaseId");
        }

        // 校验产出列表不为空
        List<OutputItem> outputs = req.getOutputs();
        if (outputs == null || outputs.isEmpty()) {
            throw new BusinessException(400, "产出商品列表 outputs 不能为空");
        }

        // 校验每个产出项
        for (int i = 0; i < outputs.size(); i++) {
            OutputItem item = outputs.get(i);
            if (item.getProductName() == null || item.getProductName().trim().isEmpty()) {
                throw new BusinessException(400, "第" + (i + 1) + "个产出项缺少 productName");
            }
            if (item.getWeightJin() == null || item.getWeightJin().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "第" + (i + 1) + "个产出项的 weightJin 必须大于0");
            }
        }

        long currentTime = System.currentTimeMillis();
        String tenantId = DEFAULT_TENANT_ID;

        // ==================== 第二步：查询采购单，校验状态 ====================
        Purchase purchase = purchaseMapper.selectById(req.getPurchaseId());
        if (purchase == null) {
            throw new BusinessException(404, "采购单不存在：" + req.getPurchaseId());
        }

        // 校验采购单状态（不允许重复加工或加工已取消的采购单）
        if ("completed".equals(purchase.getStatus())) {
            throw new BusinessException(400, "该采购单已完成加工，不能重复加工");
        }
        if ("cancelled".equals(purchase.getStatus())) {
            throw new BusinessException(400, "该采购单已取消，无法加工");
        }

        // ==================== 第三步：读取采购成本和投入重量 ====================
        // 采购成本（分）：从采购单中读取总金额
        int purchaseCostFen = purchase.getTotalAmountFen() != null ? purchase.getTotalAmountFen() : 0;

        // 原果投入重量（斤）
        BigDecimal inputWeightJin = purchase.getWeightJin() != null
                ? purchase.getWeightJin() : BigDecimal.ZERO;

        // ==================== 第四步：计算加工总成本 ====================
        ProcessingCosts costs = req.getCosts();
        int laborCostFen = (costs != null && costs.getLaborCostFen() != null) ? costs.getLaborCostFen() : 0;
        int packagingCostFen = (costs != null && costs.getPackagingCostFen() != null) ? costs.getPackagingCostFen() : 0;
        int transportCostFen = (costs != null && costs.getTransportCostFen() != null) ? costs.getTransportCostFen() : 0;
        int otherCostFen = (costs != null && costs.getOtherCostFen() != null) ? costs.getOtherCostFen() : 0;

        // 加工总成本 = 人工 + 包装 + 运输 + 其他（均为分）
        int totalProcessingCostFen = laborCostFen + packagingCostFen + transportCostFen + otherCostFen;

        // ==================== 第五步：计算总成本和总产出重量 ====================
        // 总成本 = 采购成本 + 加工总成本
        int totalCostFen = purchaseCostFen + totalProcessingCostFen;

        // 总产出重量 = 各产出品重量之和
        BigDecimal totalOutputWeightJin = BigDecimal.ZERO;
        for (OutputItem item : outputs) {
            totalOutputWeightJin = totalOutputWeightJin.add(item.getWeightJin());
        }

        // 损耗重量 = 投入重量 - 产出重量
        BigDecimal lossWeightJin = inputWeightJin.subtract(totalOutputWeightJin);

        // 损耗率（%）= 损耗重量 / 投入重量 * 100，保留2位小数
        BigDecimal lossRate = BigDecimal.ZERO;
        if (inputWeightJin.compareTo(BigDecimal.ZERO) > 0) {
            lossRate = lossWeightJin
                    .multiply(new BigDecimal("100"))
                    .divide(inputWeightJin, 2, RoundingMode.HALF_UP);
        }

        // ==================== 第六步：按重量比例分摊成本 ====================
        // 每个产出品的分配成本 = floor(totalCostFen * weightRatio)
        // 前 n-1 个用公式计算，最后一个用差额法补齐（避免分摊尾差导致总数不等）
        List<OutputProductEntry> outputEntries = new ArrayList<>();
        int allocatedSumFen = 0;

        for (int i = 0; i < outputs.size(); i++) {
            OutputItem item = outputs.get(i);
            BigDecimal weightJin = item.getWeightJin();

            // 计算该产出品的重量占比
            BigDecimal weightRatio = BigDecimal.ZERO;
            if (totalOutputWeightJin.compareTo(BigDecimal.ZERO) > 0) {
                weightRatio = weightJin.divide(totalOutputWeightJin, 10, RoundingMode.HALF_UP);
            }

            // 按重量比例分摊成本（向下取整）
            int allocatedCostFen;
            if (i == outputs.size() - 1) {
                // 最后一个用差额法补齐，确保总和等于 totalCostFen
                allocatedCostFen = totalCostFen - allocatedSumFen;
                if (allocatedCostFen < 0) {
                    allocatedCostFen = 0;
                }
            } else {
                BigDecimal rawAllocated = new BigDecimal(totalCostFen).multiply(weightRatio);
                allocatedCostFen = rawAllocated.setScale(0, RoundingMode.FLOOR).intValue();
            }
            allocatedSumFen += allocatedCostFen;

            // 计算该产出品的单位成本（分/斤），向下取整
            int unitCostFen = 0;
            if (weightJin.compareTo(BigDecimal.ZERO) > 0) {
                unitCostFen = new BigDecimal(allocatedCostFen)
                        .divide(weightJin, 0, RoundingMode.FLOOR).intValue();
            }

            // 构建产出条目（暂不填 inventoryId，待创建/更新库存后回填）
            OutputProductEntry entry = new OutputProductEntry();
            entry.productName = item.getProductName().trim();
            entry.fruitName = item.getFruitName() != null ? item.getFruitName() : purchase.getFruitName();
            entry.grade = item.getGrade() != null ? item.getGrade() : "";
            entry.spec = item.getSpec() != null ? item.getSpec() : "";
            entry.origin = item.getOrigin() != null ? item.getOrigin()
                    : (purchase.getOrigin() != null ? purchase.getOrigin() : "");
            entry.weightJin = weightJin;
            entry.allocatedCostFen = allocatedCostFen;
            entry.unitCostFen = unitCostFen;
            // inventoryId 稍后回填
            entry.inventoryId = null;

            outputEntries.add(entry);
        }

        // ==================== 第七步：生成加工单号和批次ID ====================
        long seq = getTodayProcessingOrderMaxSeq();
        String orderNo = batchNoGenerator.genProcessingOrderNo(seq);
        String batchId = batchNoGenerator.genBatchId();

        // ==================== 第八步：插入加工单记录 ====================
        ProcessingOrder order = new ProcessingOrder();
        order.setTenantId(tenantId);
        order.setOrderNo(orderNo);
        order.setPurchaseId(req.getPurchaseId());
        order.setPurchaseBatchNo(purchase.getBatchNo());
        order.setFruitName(purchase.getFruitName());
        order.setInputWeightJin(inputWeightJin);
        order.setLaborCostFen(laborCostFen);
        order.setPackagingCostFen(packagingCostFen);
        order.setTransportCostFen(transportCostFen);
        order.setOtherCostFen(otherCostFen);
        order.setTotalProcessingCostFen(totalProcessingCostFen);
        order.setPurchaseCostFen(purchaseCostFen);
        order.setTotalCostFen(totalCostFen);
        // outputProducts 先置空，等库存处理后回填
        order.setOutputProducts("[]");
        order.setTotalOutputWeightJin(totalOutputWeightJin);
        order.setLossWeightJin(lossWeightJin);
        order.setLossRate(lossRate);
        order.setStatus("completed");
        order.setProcessingDate(currentTime);
        order.setRemark(req.getRemark() != null ? req.getRemark() : "");
        order.setCreateTime(currentTime);
        order.setUpdateTime(currentTime);
        order.setOperatorId(req.getOperatorId() != null ? req.getOperatorId() : "");
        order.setOperatorName(req.getOperatorName() != null ? req.getOperatorName() : "");

        int insertResult = processingOrderMapper.insert(order);
        if (insertResult <= 0) {
            throw new BusinessException(500, "加工单插入失败");
        }

        // ==================== 第九步：遍历产出品，更新库存 ====================
        for (OutputProductEntry entry : outputEntries) {
            // 查询是否已有相同 productName + grade + spec 的库存记录
            Inventory existingInventory = findInventory(
                    entry.productName, entry.grade, entry.spec);

            // 构建新批次信息
            BatchEntry newBatch = new BatchEntry();
            newBatch.batchId = batchId;
            newBatch.purchaseBatchNo = purchase.getBatchNo();
            newBatch.inboundTime = currentTime;
            newBatch.stockJin = entry.weightJin;
            newBatch.unitCostFen = entry.unitCostFen;

            if (existingInventory != null) {
                // ---- 已有库存：移动加权平均更新成本，追加新批次 ----

                BigDecimal oldStockJin = existingInventory.getCurrentStockJin() != null
                        ? existingInventory.getCurrentStockJin() : BigDecimal.ZERO;
                int oldTotalCostFen = existingInventory.getTotalCostFen() != null
                        ? existingInventory.getTotalCostFen() : 0;

                // 新库存重量 = 旧库存 + 本次入库
                BigDecimal newStockJin = oldStockJin.add(entry.weightJin);

                // 新库存总成本 = 旧总成本 + 本次分配成本
                int newTotalCostFen = oldTotalCostFen + entry.allocatedCostFen;

                // 移动加权平均：新单位成本 = 新总成本 / 新库存重量（向下取整）
                int newUnitCostFen = 0;
                if (newStockJin.compareTo(BigDecimal.ZERO) > 0 && newTotalCostFen > 0) {
                    newUnitCostFen = new BigDecimal(newTotalCostFen)
                            .divide(newStockJin, 0, RoundingMode.FLOOR).intValue();
                }

                // 更新累计入库
                BigDecimal newTotalInboundJin = (existingInventory.getTotalInboundJin() != null
                        ? existingInventory.getTotalInboundJin() : BigDecimal.ZERO).add(entry.weightJin);

                // 解析旧批次列表，追加新批次
                List<BatchEntry> oldBatchList = parseBatchList(existingInventory.getBatchList());
                List<BatchEntry> newBatchList = new ArrayList<>(oldBatchList);
                newBatchList.add(newBatch);

                // 序列化新批次列表
                String newBatchListJson;
                try {
                    newBatchListJson = OBJECT_MAPPER.writeValueAsString(newBatchList);
                } catch (JsonProcessingException e) {
                    log.error("batch_list JSON 序列化失败, inventoryId={}", existingInventory.getId(), e);
                    throw new BusinessException(500, "库存批次序列化失败");
                }

                // 判断库存状态
                String newStatus = determineStockStatus(newStockJin,
                        existingInventory.getWarningStockJin());

                // 构建更新对象
                Inventory updateInventory = new Inventory();
                updateInventory.setId(existingInventory.getId());
                updateInventory.setCurrentStockJin(newStockJin);
                updateInventory.setUnitCostFen(newUnitCostFen);
                updateInventory.setTotalCostFen(newTotalCostFen);
                updateInventory.setTotalInboundJin(newTotalInboundJin);
                updateInventory.setLastInboundTime(currentTime);
                updateInventory.setBatchList(newBatchListJson);
                updateInventory.setStatus(newStatus);
                updateInventory.setUpdateTime(currentTime);

                inventoryMapper.updateById(updateInventory);

                // 回填库存ID
                entry.inventoryId = existingInventory.getId();

                log.info("更新库存: id={}, productName={}, 新库存={}斤, 新单位成本={}分/斤",
                        existingInventory.getId(), entry.productName,
                        newStockJin.stripTrailingZeros().toPlainString(), newUnitCostFen);
            } else {
                // ---- 新库存：创建 inventories 记录 ----

                List<BatchEntry> batchList = new ArrayList<>();
                batchList.add(newBatch);

                String batchListJson;
                try {
                    batchListJson = OBJECT_MAPPER.writeValueAsString(batchList);
                } catch (JsonProcessingException e) {
                    log.error("batch_list JSON 序列化失败, productName={}", entry.productName, e);
                    throw new BusinessException(500, "库存批次序列化失败");
                }

                // 判断库存状态
                String newStatus = determineStockStatus(entry.weightJin, null);

                Inventory newInventory = new Inventory();
                newInventory.setTenantId(tenantId);
                newInventory.setProductName(entry.productName);
                newInventory.setFruitName(entry.fruitName);
                newInventory.setGrade(entry.grade);
                newInventory.setSpec(entry.spec);
                newInventory.setOrigin(entry.origin);
                newInventory.setCurrentStockJin(entry.weightJin);
                newInventory.setUnitCostFen(entry.unitCostFen);
                newInventory.setTotalCostFen(entry.allocatedCostFen);
                // 建议售价暂设为成本的120%，后续可手动调整
                newInventory.setSuggestedPriceFen((int) (entry.unitCostFen * 1.2));
                newInventory.setBatchList(batchListJson);
                newInventory.setTotalInboundJin(entry.weightJin);
                newInventory.setTotalOutboundJin(BigDecimal.ZERO);
                newInventory.setWarningStockJin(BigDecimal.ZERO);
                newInventory.setStatus(newStatus);
                newInventory.setLastInboundTime(currentTime);
                newInventory.setLastOutboundTime(0L);
                newInventory.setRemark("");
                newInventory.setCreateTime(currentTime);
                newInventory.setUpdateTime(currentTime);

                int insertInvResult = inventoryMapper.insert(newInventory);
                if (insertInvResult <= 0) {
                    throw new BusinessException(500, "库存记录插入失败：" + entry.productName);
                }

                // 回填库存ID
                entry.inventoryId = newInventory.getId();

                log.info("创建新库存: id={}, productName={}, 库存={}斤, 单位成本={}分/斤",
                        newInventory.getId(), entry.productName,
                        entry.weightJin.stripTrailingZeros().toPlainString(), entry.unitCostFen);
            }
        }

        // ==================== 第十步：回填 output_products（含 inventoryId） ====================
        String outputProductsJson;
        try {
            outputProductsJson = OBJECT_MAPPER.writeValueAsString(outputEntries);
        } catch (JsonProcessingException e) {
            log.error("output_products JSON 序列化失败, orderId={}", order.getId(), e);
            throw new BusinessException(500, "产出商品序列化失败");
        }

        ProcessingOrder updateOrder = new ProcessingOrder();
        updateOrder.setId(order.getId());
        updateOrder.setOutputProducts(outputProductsJson);
        updateOrder.setUpdateTime(currentTime);
        processingOrderMapper.updateById(updateOrder);

        // 同步更新返回对象
        order.setOutputProducts(outputProductsJson);

        // ==================== 第十一步：更新采购单状态为 completed ====================
        Purchase updatePurchase = new Purchase();
        updatePurchase.setId(req.getPurchaseId());
        updatePurchase.setStatus("completed");
        updatePurchase.setUpdateTime(currentTime);
        purchaseMapper.updateById(updatePurchase);

        log.info("加工入库成功：加工单号={}, 采购批次号={}, 总成本={}分, 产出{}项",
                orderNo, purchase.getBatchNo(), totalCostFen, outputEntries.size());

        return order;
    }

    /**
     * 作废加工单
     * 
     * 严格按照 JS 云函数对应逻辑翻译
     * 仅允许状态不是 completed/cancelled 的订单作废
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId) {
        // 参数校验
        if (orderId == null) {
            throw new BusinessException(400, "缺少订单 ID 参数");
        }

        // 查询加工单
        ProcessingOrder order = processingOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "加工单不存在");
        }

        // 校验状态：已完成的订单不允许作废
        if ("completed".equals(order.getStatus())) {
            throw new BusinessException(400, "该加工单已完成，不允许作废");
        }
        if ("cancelled".equals(order.getStatus())) {
            throw new BusinessException(400, "该加工单已作废，不能重复操作");
        }

        // 更新状态为 cancelled，记录作废时间
        long currentTime = System.currentTimeMillis();
        ProcessingOrder updateOrder = new ProcessingOrder();
        updateOrder.setId(orderId);
        updateOrder.setStatus("cancelled");
        updateOrder.setCancelTime(currentTime);
        updateOrder.setUpdateTime(currentTime);
        processingOrderMapper.updateById(updateOrder);

        log.info("加工单 {} (单号: {}) 已作废", orderId, order.getOrderNo());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据 productName + grade + spec 查找库存记录
     * 
     * 这三个字段组合唯一确定一个 SKU
     * 
     * @param productName 成品名称
     * @param grade       等级
     * @param spec        规格
     * @return 匹配的库存记录，或 null
     */
    private Inventory findInventory(String productName, String grade, String spec) {
        QueryWrapper<Inventory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("product_name", productName);
        queryWrapper.eq("grade", grade != null ? grade : "");
        queryWrapper.eq("spec", spec != null ? spec : "");

        // 使用 last("LIMIT 1") 确保只取一条
        queryWrapper.last("LIMIT 1");

        return inventoryMapper.selectOne(queryWrapper);
    }

    /**
     * 判断库存状态
     * 
     * @param stockJin       当前库存重量
     * @param warningStockJin 预警库存量
     * @return 状态：normal / low_stock / out_of_stock
     */
    private String determineStockStatus(BigDecimal stockJin, BigDecimal warningStockJin) {
        if (stockJin == null || stockJin.compareTo(BigDecimal.ZERO) <= 0) {
            return "out_of_stock";
        }
        if (warningStockJin != null && warningStockJin.compareTo(BigDecimal.ZERO) > 0
                && stockJin.compareTo(warningStockJin) <= 0) {
            return "low_stock";
        }
        return "normal";
    }

    /**
     * 将 batch_list JSON 字符串解析为 BatchEntry 列表
     * 
     * batch_list 在数据库中存为 JSON 字符串，需要手动反序列化
     */
    private List<BatchEntry> parseBatchList(String batchListJson) {
        if (batchListJson == null || batchListJson.trim().isEmpty()
                || "[]".equals(batchListJson.trim())) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(batchListJson, new TypeReference<List<BatchEntry>>() {});
        } catch (JsonProcessingException e) {
            log.error("batch_list JSON 反序列化失败: {}", batchListJson, e);
            throw new BusinessException(500, "库存批次数据格式异常");
        }
    }

    /**
     * 获取当天加工单的最大序号 + 1
     * 
     * 格式：JG + yyyyMMdd + 4位序号
     */
    private long getTodayProcessingOrderMaxSeq() {
        String todayDateStr = com.saas.fruit.utils.DateUtil.getDateStr();
        String prefix = "JG" + todayDateStr;

        QueryWrapper<ProcessingOrder> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("order_no", prefix);

        long count = processingOrderMapper.selectCount(queryWrapper);
        return count + 1;
    }

    // ==================== 内部数据类 ====================

    /**
     * 产出商品条目（对应 output_products JSON 中每一项的结构）
     * 
     * output_products JSON 示例：
     * [{
     *   "productName": "红富士苹果精品装",
     *   "fruitName": "红富士苹果",
     *   "grade": "A级",
     *   "spec": "10斤/箱",
     *   "origin": "烟台栖霞",
     *   "weightJin": 85.5,
     *   "allocatedCostFen": 42750,
     *   "unitCostFen": 500,
     *   "inventoryId": 1
     * }]
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class OutputProductEntry {
        /** 成品名称 */
        private String productName;
        /** 水果名称 */
        private String fruitName;
        /** 等级 */
        private String grade;
        /** 规格 */
        private String spec;
        /** 产地 */
        private String origin;
        /** 产出重量（斤） */
        private BigDecimal weightJin;
        /** 分摊成本（分） */
        private int allocatedCostFen;
        /** 单位成本（分/斤） */
        private int unitCostFen;
        /** 关联库存ID（入库后回填） */
        private Long inventoryId;
    }

    /**
     * 批次条目（对应 batch_list JSON 中每一项的结构）
     * 
     * batch_list JSON 示例：
     * [{
     *   "batchId": "BATCH1234567890001",
     *   "purchaseBatchNo": "CG202605010001",
     *   "inboundTime": 1714608000000,
     *   "stockJin": 100.5,
     *   "unitCostFen": 500
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
        /** 入库时间戳（用于 FIFO 排序） */
        private Long inboundTime;
        /** 该批次的当前库存重量（斤） */
        private BigDecimal stockJin;
        /** 该批次的单位成本（分/斤） */
        private Integer unitCostFen;
    }
}
