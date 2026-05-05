package com.saas.fruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.fruit.common.BusinessException;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.FinanceReceiveRequest;
import com.saas.fruit.dto.request.FinanceReceiveRequest.WriteOffDetail;
import com.saas.fruit.entity.Customer;
import com.saas.fruit.entity.FinancialLedger;
import com.saas.fruit.entity.SalesOrder;
import com.saas.fruit.mapper.CustomerMapper;
import com.saas.fruit.mapper.FinancialLedgerMapper;
import com.saas.fruit.mapper.SalesOrderMapper;
import com.saas.fruit.service.FinanceService;
import com.saas.fruit.utils.BatchNoGenerator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务服务实现类
 * 
 * 严格按照 JS 云函数 finance_receive 的逻辑翻译
 * 
 * 核心要点：
 * - 金额统一以"分"（Integer）为单位存储，避免浮点数精度问题
 * - 全程使用 @Transactional 保证数据一致性（替代 JS 中的 db.runTransaction）
 * - 收款核销：支持一笔收款同时核销多笔销售订单的欠款
 * - writeOffDetails 字段使用 Jackson ObjectMapper 处理 JSON
 * - 客户 totalDebtFen 在核销后需重新计算（取核销后余额）
 */
@Slf4j
@Service
public class FinanceServiceImpl implements FinanceService {

    @Autowired
    private FinancialLedgerMapper financialLedgerMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private BatchNoGenerator batchNoGenerator;

    /** Jackson ObjectMapper，用于 writeOffDetails JSON 序列化 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 默认租户ID（二期 SaaS 化时从上下文获取） */
    private static final String DEFAULT_TENANT_ID = "default";

    /** 有效的收款方式列表 */
    private static final List<String> VALID_PAYMENT_METHODS = List.of(
            "wechat", "alipay", "cash", "bank", "transfer");

    /**
     * 客户收款（记录回款并核销欠款）
     * 
     * @Transactional 确保所有操作在同一事务中执行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinancialLedger receive(FinanceReceiveRequest req) {
        // ==================== 第一步：入参校验 ====================

        // 校验客户ID
        if (req.getCustomerId() == null) {
            throw new BusinessException(400, "缺少必要参数：customerId");
        }

        // 校验收款金额必须大于0
        if (req.getAmountFen() == null || req.getAmountFen() <= 0) {
            throw new BusinessException(400, "收款金额 amountFen 必须大于0");
        }

        // 校验收款方式
        String paymentMethod = req.getPaymentMethod();
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            throw new BusinessException(400, "缺少必要参数：paymentMethod");
        }
        if (!VALID_PAYMENT_METHODS.contains(paymentMethod)) {
            throw new BusinessException(400,
                    "收款方式 paymentMethod 无效，支持：wechat/alipay/cash/bank/transfer");
        }

        long currentTime = System.currentTimeMillis();
        long receiveTime = req.getReceiveTime() != null ? req.getReceiveTime() : currentTime;
        String tenantId = DEFAULT_TENANT_ID;
        int amountFen = req.getAmountFen();
        List<WriteOffDetail> writeOffDetails = req.getWriteOffDetails();

        // ==================== 第二步：查询客户信息，校验状态 ====================
        Customer customer = customerMapper.selectById(req.getCustomerId());
        if (customer == null) {
            throw new BusinessException(404, "客户不存在：" + req.getCustomerId());
        }

        // 校验客户状态（inactive/blacklist/deleted 不允许收款）
        if ("inactive".equals(customer.getStatus())) {
            throw new BusinessException(403, "该客户已停用，无法收款");
        }
        if ("blacklist".equals(customer.getStatus())) {
            throw new BusinessException(403, "该客户已被列入黑名单，无法收款");
        }
        if ("deleted".equals(customer.getStatus())) {
            throw new BusinessException(403, "该客户已删除，无法收款");
        }

        // ==================== 第三步：处理核销明细 ====================
        // 记录本次核销前的客户总欠款（收款前余额）
        int balanceBeforeFen = customer.getTotalDebtFen() != null ? customer.getTotalDebtFen() : 0;

        List<WriteOffDetailEntry> writeOffEntries = new ArrayList<>();
        int totalWriteOffAmountFen = 0;

        if (writeOffDetails != null && !writeOffDetails.isEmpty()) {
            for (WriteOffDetail detail : writeOffDetails) {
                if (detail.getOrderId() == null) {
                    throw new BusinessException(400, "核销明细中缺少 orderId");
                }
                if (detail.getWriteOffAmountFen() == null || detail.getWriteOffAmountFen() <= 0) {
                    throw new BusinessException(400,
                            "核销明细中订单 " + detail.getOrderId() + " 的核销金额必须大于0");
                }

                // 查询销售订单
                SalesOrder salesOrder = salesOrderMapper.selectById(detail.getOrderId());
                if (salesOrder == null) {
                    throw new BusinessException(404, "销售订单不存在：" + detail.getOrderId());
                }

                // 校验该订单属于当前客户
                if (!req.getCustomerId().equals(salesOrder.getCustomerId())) {
                    throw new BusinessException(400,
                            "订单 " + salesOrder.getOrderNo() + " 不属于该客户，无法核销");
                }

                // 校验订单不是已付清状态
                if ("paid".equals(salesOrder.getPaymentStatus())) {
                    throw new BusinessException(400,
                            "订单 " + salesOrder.getOrderNo() + " 已付清，无需核销");
                }

                // 校验订单未取消
                if ("cancelled".equals(salesOrder.getStatus())) {
                    throw new BusinessException(400,
                            "订单 " + salesOrder.getOrderNo() + " 已取消，无法核销");
                }

                int writeOffAmountFen = detail.getWriteOffAmountFen();
                int debtBeforeFen = salesOrder.getDebtAmountFen() != null ? salesOrder.getDebtAmountFen() : 0;

                // 校验核销金额不超过欠款金额
                if (writeOffAmountFen > debtBeforeFen) {
                    throw new BusinessException(400,
                            "订单 " + salesOrder.getOrderNo() + " 核销金额(" + writeOffAmountFen
                                    + "分)超过欠款金额(" + debtBeforeFen + "分)");
                }

                // 计算核销后欠款
                int debtAfterFen = debtBeforeFen - writeOffAmountFen;
                int newPaidAmountFen = (salesOrder.getPaidAmountFen() != null
                        ? salesOrder.getPaidAmountFen() : 0) + writeOffAmountFen;

                // 计算新的收款状态
                String newPaymentStatus;
                if (debtAfterFen <= 0) {
                    newPaymentStatus = "paid";
                } else {
                    newPaymentStatus = "partial";
                }

                // 构建核销条目（用于 writeOffDetails JSON 和后续更新）
                WriteOffDetailEntry entry = new WriteOffDetailEntry();
                entry.orderId = salesOrder.getId();
                entry.orderNo = salesOrder.getOrderNo();
                entry.amountFen = writeOffAmountFen;
                entry.orderDebtBeforeFen = debtBeforeFen;
                entry.orderDebtAfterFen = debtAfterFen;
                entry.newPaymentStatus = newPaymentStatus;
                entry.newPaidAmountFen = newPaidAmountFen;
                entry.newDebtAmountFen = debtAfterFen;

                writeOffEntries.add(entry);
                totalWriteOffAmountFen += writeOffAmountFen;
            }

            // 校验核销总额不超过收款金额
            if (totalWriteOffAmountFen > amountFen) {
                throw new BusinessException(400,
                        "核销总额(" + totalWriteOffAmountFen + "分)超过收款金额(" + amountFen + "分)");
            }
        }

        // 收款后余额 = 收款前余额 - 收款金额（收款减少应收账款）
        int balanceAfterFen = balanceBeforeFen - amountFen;
        if (balanceAfterFen < 0) {
            // 多收款项（客户余额为负，表示预收款），允许但不常见
            log.warn("客户 {} 收款后余额为负({}分)，可能存在多收款", customer.getName(), balanceAfterFen);
        }

        // ==================== 第四步：生成收款流水号并插入财务流水 ====================
        long seq = getTodayReceivableLedgerMaxSeq();
        String ledgerNo = batchNoGenerator.genReceiveLedgerNo(seq);

        // 将核销明细序列化为 JSON
        // 注意：writeOffDetails JSON 中只保存核销相关数据，不保存 newPaymentStatus 等内部状态
        List<Map<String, Object>> writeOffDetailsForJson = new ArrayList<>();
        for (WriteOffDetailEntry entry : writeOffEntries) {
            Map<String, Object> detailMap = new HashMap<>();
            detailMap.put("orderId", entry.orderId);
            detailMap.put("orderNo", entry.orderNo);
            detailMap.put("amountFen", entry.amountFen);
            detailMap.put("orderDebtBeforeFen", entry.orderDebtBeforeFen);
            detailMap.put("orderDebtAfterFen", entry.orderDebtAfterFen);
            writeOffDetailsForJson.add(detailMap);
        }

        String writeOffDetailsJson;
        try {
            writeOffDetailsJson = OBJECT_MAPPER.writeValueAsString(writeOffDetailsForJson);
        } catch (JsonProcessingException e) {
            log.error("核销明细 JSON 序列化失败", e);
            throw new BusinessException(500, "财务流水生成失败：核销明细序列化异常");
        }

        // 构建财务流水记录
        FinancialLedger ledger = new FinancialLedger();
        ledger.setTenantId(tenantId);
        ledger.setLedgerNo(ledgerNo);
        ledger.setLedgerType("receivable");         // 应收账款
        ledger.setTransactionType("payment_received");
        ledger.setAmountFen(amountFen);
        ledger.setBalanceBeforeFen(balanceBeforeFen);
        ledger.setBalanceAfterFen(balanceAfterFen);
        ledger.setRelatedPartyType("customer");
        ledger.setRelatedPartyId(req.getCustomerId());
        ledger.setRelatedPartyName(customer.getName());
        // 多笔核销时，relatedOrderType 和 relatedOrderId 取第一笔（主要关联）
        if (!writeOffEntries.isEmpty()) {
            WriteOffDetailEntry firstEntry = writeOffEntries.get(0);
            ledger.setRelatedOrderType("sales_order");
            ledger.setRelatedOrderId(firstEntry.orderId);
            ledger.setRelatedOrderNo(firstEntry.orderNo);
        } else {
            // 无核销明细时（纯收款，不关联具体订单）
            ledger.setRelatedOrderType("none");
            ledger.setRelatedOrderId(null);
            ledger.setRelatedOrderNo("");
        }
        ledger.setWriteOffDetails(writeOffDetailsJson);
        ledger.setPaymentMethod(paymentMethod);
        ledger.setTransactionTime(receiveTime);
        ledger.setRemark(req.getRemark() != null ? req.getRemark() : "");
        ledger.setCreateTime(currentTime);
        ledger.setOperatorId(req.getOperatorId() != null ? req.getOperatorId() : "");
        ledger.setOperatorName(req.getOperatorName() != null ? req.getOperatorName() : "");

        int insertResult = financialLedgerMapper.insert(ledger);
        if (insertResult <= 0) {
            throw new BusinessException(500, "财务流水插入失败");
        }

        // ==================== 第五步：逐笔更新销售订单 ====================
        for (WriteOffDetailEntry entry : writeOffEntries) {
            SalesOrder updateOrder = new SalesOrder();
            updateOrder.setId(entry.orderId);
            updateOrder.setPaymentStatus(entry.newPaymentStatus);
            updateOrder.setPaidAmountFen(entry.newPaidAmountFen);
            updateOrder.setDebtAmountFen(entry.newDebtAmountFen);
            updateOrder.setUpdateTime(currentTime);
            salesOrderMapper.updateById(updateOrder);

            log.info("核销订单: orderId={}, orderNo={}, 核销金额={}分, 剩余欠款={}分, 状态={}",
                    entry.orderId, entry.orderNo, entry.amountFen,
                    entry.newDebtAmountFen, entry.newPaymentStatus);
        }

        // ==================== 第六步：更新客户统计信息 ====================
        // 使用 CustomerMapper.updateReceiveStats 原子更新
        // totalDebtFen 设置为核销后的余额，totalPaidFen 累加本次收款金额
        customerMapper.updateReceiveStats(
                req.getCustomerId(),
                balanceAfterFen,       // totalDebtFen = 收款后余额
                amountFen,             // totalPaidFen 累加
                currentTime            // updateTime
        );

        log.info("收款成功：流水号={}, 客户={}, 金额={}分, 核销{}笔订单, 余额前={}分, 余额后={}分",
                ledgerNo, customer.getName(), amountFen,
                writeOffEntries.size(), balanceBeforeFen, balanceAfterFen);

        return ledger;
    }

    /**
     * 分页查询财务流水
     * 
     * 按 transactionTime 倒序排列
     */
    @Override
    public PageResponse<FinancialLedger> ledgers(int page, int pageSize) {
        // 参数校验
        if (page <= 0) {
            page = 1;
        }
        if (pageSize <= 0 || pageSize > 100) {
            pageSize = 20;
        }

        // 构建分页查询条件
        Page<FinancialLedger> pageParam = new Page<>(page, pageSize);

        QueryWrapper<FinancialLedger> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("transaction_time");

        // 执行分页查询
        Page<FinancialLedger> resultPage = financialLedgerMapper.selectPage(pageParam, queryWrapper);

        // 构建分页响应
        return PageResponse.success(
                resultPage.getRecords(),
                resultPage.getTotal(),
                page,
                pageSize);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取当天应收（收款）流水号的最大序号 + 1
     * 
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

    // ==================== 内部数据类 ====================

    /**
     * 核销明细条目（内部使用，含处理后的状态信息）
     * 
     * writeOffDetails JSON 示例：
     * [{
     *   "orderId": 1,
     *   "orderNo": "XS202605010001",
     *   "amountFen": 5000,
     *   "orderDebtBeforeFen": 10000,
     *   "orderDebtAfterFen": 5000
     * }]
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class WriteOffDetailEntry {
        /** 订单ID */
        private Long orderId;
        /** 订单号 */
        private String orderNo;
        /** 本次核销金额（分） */
        private int amountFen;
        /** 核销前欠款（分） */
        private int orderDebtBeforeFen;
        /** 核销后欠款（分） */
        private int orderDebtAfterFen;
        /** 新的收款状态（用于更新订单） */
        private String newPaymentStatus;
        /** 新的已付金额（用于更新订单） */
        private int newPaidAmountFen;
        /** 新的欠款金额（用于更新订单） */
        private int newDebtAmountFen;
    }
}
