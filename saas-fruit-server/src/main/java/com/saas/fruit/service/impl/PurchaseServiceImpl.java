package com.saas.fruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.fruit.common.BusinessException;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.PurchaseCreateRequest;
import com.saas.fruit.entity.FinancialLedger;
import com.saas.fruit.entity.Purchase;
import com.saas.fruit.entity.Supplier;
import com.saas.fruit.mapper.FinancialLedgerMapper;
import com.saas.fruit.mapper.PurchaseMapper;
import com.saas.fruit.mapper.SupplierMapper;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.service.PurchaseService;
import com.saas.fruit.utils.BatchNoGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购单服务实现类
 * 
 * 严格按照 JS 云函数 purchase_create 和 purchase_cancel 的逻辑翻译
 * 
 * 核心要点：
 * - 金额统一以"分"（Integer）为单位存储，避免浮点数精度问题
 * - 全程使用 @Transactional 保证数据一致性（替代 JS 中的 db.runTransaction）
 * - 供应商校验：存在性 + 状态不能为 inactive/blacklist
 * - 财务流水生成：先全额应付，再付款核销（两笔独立流水）
 */
@Slf4j
@Service
public class PurchaseServiceImpl implements PurchaseService {

    @Autowired
    private PurchaseMapper purchaseMapper;

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private FinancialLedgerMapper financialLedgerMapper;

    @Autowired
    private BatchNoGenerator batchNoGenerator;

    /** Jackson ObjectMapper，用于 JSON 序列化（如 writeOffDetails 字段） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 默认租户ID（二期 SaaS 化时从上下文获取） */
    private static final String DEFAULT_TENANT_ID = "default";

    /**
     * 分页查询采购单列表
     */
    @Override
    public PageResponse<Purchase> list(String keyword, String status, Long supplierId, int page, int pageSize) {
        String tenantId = LoginUserContext.getTenantId();

        QueryWrapper<Purchase> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("tenant_id", tenantId);

        // 关键词搜索：批次号/原果名称/供应商名称
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(w -> w
                    .like("batch_no", keyword)
                    .or().like("fruit_name", keyword)
                    .or().like("supplier_name", keyword));
        }

        // 状态筛选
        if (status != null && !status.trim().isEmpty()) {
            queryWrapper.eq("status", status.trim());
        }

        // 供应商筛选
        if (supplierId != null) {
            queryWrapper.eq("supplier_id", supplierId);
        }

        queryWrapper.orderByDesc("purchase_date");

        Page<Purchase> pageResult = purchaseMapper.selectPage(
                new Page<>(page + 1, pageSize), queryWrapper);

        return PageResponse.success(pageResult.getRecords(), pageResult.getTotal(), page, pageSize);
    }

    /**
     * 创建采购单
     * 
     * @Transactional 确保所有数据库操作在同一事务中执行，失败时自动回滚
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Purchase create(PurchaseCreateRequest req) {
        // ==================== 第一步：入参校验 ====================

        // 校验供应商ID
        if (req.getSupplierId() == null) {
            throw new BusinessException(400, "缺少必要参数：supplierId");
        }

        // 校验供应商名称
        if (req.getSupplierName() == null || req.getSupplierName().trim().isEmpty()) {
            throw new BusinessException(400, "缺少必要参数：supplierName");
        }

        // 校验原果名称
        if (req.getFruitName() == null || req.getFruitName().trim().isEmpty()) {
            throw new BusinessException(400, "缺少必要参数：fruitName");
        }

        // 校验重量（必须大于0）
        if (req.getWeightJin() == null || req.getWeightJin().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "重量 weightJin 必须大于0");
        }

        // 校验单价（必须存在且 >= 0）
        if (req.getUnitPriceFen() == null || req.getUnitPriceFen() < 0) {
            throw new BusinessException(400, "单价 unitPriceFen 无效");
        }

        // 校验总金额（必须存在且 >= 0）
        if (req.getTotalAmountFen() == null || req.getTotalAmountFen() < 0) {
            throw new BusinessException(400, "总金额 totalAmountFen 无效");
        }

        // 校验付款状态
        String paymentStatus = req.getPaymentStatus() != null ? req.getPaymentStatus() : "unpaid";
        if (!"unpaid".equals(paymentStatus) && !"partial".equals(paymentStatus) && !"paid".equals(paymentStatus)) {
            throw new BusinessException(400, "付款状态 paymentStatus 必须是 unpaid、partial 或 paid");
        }

        // 转换金额，确保整型（对应 JS 中的 parseInt）
        int totalAmountFen = req.getTotalAmountFen();
        int paidAmountFen = req.getPaidAmountFen() != null ? req.getPaidAmountFen() : 0;
        int debtAmountFen = req.getDebtAmountFen() != null ? req.getDebtAmountFen() : 0;
        int unitPriceFen = req.getUnitPriceFen();

        // 校验已付金额不能超过总金额（对应 JS 第149-154行）
        if (paidAmountFen > totalAmountFen) {
            throw new BusinessException(400, "已付金额不能超过总金额");
        }

        long currentTime = System.currentTimeMillis();
        String tenantId = DEFAULT_TENANT_ID;

        // ==================== 第二步：查询供应商信息 ====================
        // 对应 JS 第165-183行
        Supplier supplier = supplierMapper.selectById(req.getSupplierId());
        if (supplier == null) {
            throw new BusinessException(404, "供应商不存在：" + req.getSupplierId());
        }

        // 校验供应商状态（对应 JS 第176-183行）
        if ("inactive".equals(supplier.getStatus())) {
            throw new BusinessException(403, "该供应商已停用，无法采购");
        }
        if ("blacklist".equals(supplier.getStatus())) {
            throw new BusinessException(403, "该供应商已被列入黑名单，无法采购");
        }

        // ==================== 第三步：生成采购批次号并创建采购单 ====================
        // 对应 JS 第185-213行
        
        // 查询当天最大序号，生成批次号
        long seq = getTodayPurchaseMaxSeq();
        String batchNo = batchNoGenerator.genPurchaseBatchNo(seq);

        // 构建采购单实体（对应 JS 第188-208行）
        Purchase purchase = new Purchase();
        purchase.setTenantId(tenantId);
        purchase.setBatchNo(batchNo);
        purchase.setSupplierId(req.getSupplierId());
        purchase.setSupplierName(req.getSupplierName().trim());
        purchase.setFruitName(req.getFruitName().trim());
        purchase.setOrigin(req.getOrigin() != null ? req.getOrigin() : "");
        purchase.setWeightJin(req.getWeightJin());
        purchase.setUnitPriceFen(unitPriceFen);
        purchase.setTotalAmountFen(totalAmountFen);
        purchase.setPaymentStatus(paymentStatus);
        purchase.setPaidAmountFen(paidAmountFen);
        purchase.setDebtAmountFen(debtAmountFen);
        purchase.setPurchaseDate(currentTime);
        purchase.setStatus("pending"); // 待加工状态
        purchase.setRemark(req.getRemark() != null ? req.getRemark() : "");
        purchase.setCreateTime(currentTime);
        purchase.setUpdateTime(currentTime);
        purchase.setOperatorId(req.getOperatorId() != null ? req.getOperatorId() : "");
        purchase.setOperatorName(req.getOperatorName() != null ? req.getOperatorName() : "");

        // 插入采购单（对应 JS 第211-212行）
        int insertResult = purchaseMapper.insert(purchase);
        if (insertResult <= 0) {
            throw new BusinessException(500, "采购单插入失败");
        }

        // ==================== 第四步：更新供应商统计信息 ====================
        // 对应 JS 第215-233行
        // 使用 SupplierMapper.updatePurchaseStats 方法，该方法使用原子性的 SQL UPDATE
        // 确保并发安全（通过数据库级别的原子操作）
        supplierMapper.updatePurchaseStats(
                req.getSupplierId(),
                totalAmountFen,      // totalPurchaseFen
                paidAmountFen,       // paidAmountFen
                debtAmountFen,       // debtAmountFen
                currentTime,         // lastPurchaseTime
                currentTime          // updateTime
        );

        // ==================== 第五步：生成财务流水 ====================
        // 对应 JS 第235-306行
        // 核心原则：先产生全额应付，再核销付款
        // 将一笔交易拆分为两个先后发生的独立事件：
        //   事件A：产生全额应付（采购了货，账面欠款增加）
        //   事件B：发生付款核销（向供应商付款，账面欠款减少）

        int currentBalanceFen = supplier.getTotalDebtFen() != null ? supplier.getTotalDebtFen() : 0;

        // 5.1 生成"全额应付"的采购欠款流水（只要总金额 > 0）
        // 对应 JS 第242-270行
        if (totalAmountFen > 0) {
            long ledgerSeq = getTodayPayableLedgerMaxSeq();
            String ledgerNoDebt = batchNoGenerator.genPayLedgerNo(ledgerSeq);
            int balanceAfterDebtFen = currentBalanceFen + totalAmountFen;

            FinancialLedger debtLedger = new FinancialLedger();
            debtLedger.setTenantId(tenantId);
            debtLedger.setLedgerNo(ledgerNoDebt);
            debtLedger.setLedgerType("payable");          // 应付账款
            debtLedger.setTransactionType("purchase_debt");
            debtLedger.setAmountFen(totalAmountFen);       // 全额
            debtLedger.setBalanceBeforeFen(currentBalanceFen);
            debtLedger.setBalanceAfterFen(balanceAfterDebtFen);
            debtLedger.setRelatedPartyType("supplier");
            debtLedger.setRelatedPartyId(req.getSupplierId());
            debtLedger.setRelatedPartyName(req.getSupplierName().trim());
            debtLedger.setRelatedOrderType("purchase_order");
            debtLedger.setRelatedOrderId(purchase.getId());
            debtLedger.setRelatedOrderNo(batchNo);
            // 欠款流水不需要 writeOffDetails
            debtLedger.setTransactionTime(currentTime);
            debtLedger.setRemark("采购单 " + batchNo + " 产生应付");
            debtLedger.setCreateTime(currentTime);
            debtLedger.setOperatorId(req.getOperatorId() != null ? req.getOperatorId() : "");
            debtLedger.setOperatorName(req.getOperatorName() != null ? req.getOperatorName() : "");

            financialLedgerMapper.insert(debtLedger);
            currentBalanceFen = balanceAfterDebtFen; // 更新当前余额指针
        }

        // 5.2 如果有已付金额，生成"付款核销"流水
        // 对应 JS 第273-306行
        if (paidAmountFen > 0) {
            long ledgerSeq = getTodayPayableLedgerMaxSeq();
            String ledgerNoPayment = batchNoGenerator.genPayLedgerNo(ledgerSeq);
            int balanceAfterPaymentFen = currentBalanceFen - paidAmountFen; // 付款减少欠款

            // 构建核销明细（对应 JS 第291-297行）
            List<Map<String, Object>> writeOffDetailsList = new ArrayList<>();
            Map<String, Object> writeOffDetail = new HashMap<>();
            writeOffDetail.put("orderId", purchase.getId());
            writeOffDetail.put("orderNo", batchNo);
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
            paymentLedger.setLedgerType("payable");          // 应付账款
            paymentLedger.setTransactionType("payment_made");
            paymentLedger.setAmountFen(paidAmountFen);
            paymentLedger.setBalanceBeforeFen(currentBalanceFen);
            paymentLedger.setBalanceAfterFen(balanceAfterPaymentFen);
            paymentLedger.setRelatedPartyType("supplier");
            paymentLedger.setRelatedPartyId(req.getSupplierId());
            paymentLedger.setRelatedPartyName(req.getSupplierName().trim());
            paymentLedger.setRelatedOrderType("purchase_order");
            paymentLedger.setRelatedOrderId(purchase.getId());
            paymentLedger.setRelatedOrderNo(batchNo);
            paymentLedger.setWriteOffDetails(writeOffDetailsJson);
            paymentLedger.setTransactionTime(currentTime + 1); // 稍微错开1毫秒保证排序
            paymentLedger.setRemark("采购单 " + batchNo + " 付款");
            paymentLedger.setCreateTime(currentTime);
            paymentLedger.setOperatorId(req.getOperatorId() != null ? req.getOperatorId() : "");
            paymentLedger.setOperatorName(req.getOperatorName() != null ? req.getOperatorName() : "");

            financialLedgerMapper.insert(paymentLedger);
        }

        // 返回创建的采购单（对应 JS 第309-324行）
        return purchase;
    }

    /**
     * 作废采购单
     * 
     * 严格按照 JS 云函数 purchase_cancel 的逻辑翻译
     * 仅允许状态为 pending（待加工）的订单作废
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId) {
        // 参数校验（对应 JS 第57-62行）
        if (orderId == null) {
            throw new BusinessException(400, "缺少订单 ID 参数");
        }

        // 1. 查询采购订单（对应 JS 第68-77行）
        Purchase order = purchaseMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }

        // 2. 检查订单状态：仅允许 pending（待加工）状态的订单作废
        // 对应 JS 第82-87行
        if (!"pending".equals(order.getStatus())) {
            String statusDesc = "completed".equals(order.getStatus()) ? "已完成" : "已取消";
            throw new BusinessException(400, "当前订单状态为" + statusDesc + "，不允许作废");
        }

        // 3. 更新订单状态为已取消，记录作废时间
        // 对应 JS 第157-163行
        Purchase updatePurchase = new Purchase();
        updatePurchase.setId(orderId);
        updatePurchase.setStatus("cancelled");
        updatePurchase.setCancelTime(System.currentTimeMillis());
        updatePurchase.setUpdateTime(System.currentTimeMillis());
        purchaseMapper.updateById(updatePurchase);

        log.info("采购单 {} (批次号: {}) 已作废", orderId, order.getBatchNo());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取当天采购批次号的最大序号 + 1
     * 
     * 对应 JS generateBatchNo 函数（第26-40行）
     * JS 实现：通过正则匹配 prefix 前缀的批次号数量，count + 1 作为序号
     * Java 实现：通过 SQL LIKE 查询当天前缀的采购单数量
     * 
     * 注意：序号生成并非绝对安全，高并发下可能重复。
     * 建议 batch_no 字段设置唯一索引（UNIQUE KEY），发生重复时重试。
     */
    private long getTodayPurchaseMaxSeq() {
        String todayDateStr = com.saas.fruit.utils.DateUtil.getDateStr();
        String prefix = "CG" + todayDateStr;

        QueryWrapper<Purchase> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("batch_no", prefix);

        long count = purchaseMapper.selectCount(queryWrapper);
        return count + 1; // 对应 JS: (countResult.total + 1)
    }

    /**
     * 获取当天应付（付款）流水号的最大序号 + 1
     * 
     * 对应 JS generateLedgerNo 函数（第46-60行）
     * 格式：FK + yyyyMMdd + 4位序号
     */
    private long getTodayPayableLedgerMaxSeq() {
        String todayDateStr = com.saas.fruit.utils.DateUtil.getDateStr();
        String prefix = "FK" + todayDateStr;

        QueryWrapper<FinancialLedger> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("ledger_no", prefix);

        long count = financialLedgerMapper.selectCount(queryWrapper);
        return count + 1;
    }
}
