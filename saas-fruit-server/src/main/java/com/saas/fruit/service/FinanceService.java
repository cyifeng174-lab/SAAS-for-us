package com.saas.fruit.service;

import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.FinanceReceiveRequest;
import com.saas.fruit.entity.FinancialLedger;

/**
 * 财务服务接口
 * 负责客户收款、财务流水查询等业务逻辑
 */
public interface FinanceService {

    /**
     * 客户收款（记录回款）
     * 
     * 核心流程：
     * 1. 入参校验（customerId、amountFen>0、paymentMethod 有效）
     * 2. 查询客户信息，校验状态
     * 3. 处理核销明细（writeOffDetails）：
     *    - 遍历每个核销项
     *    - 查询对应的销售订单，校验属于该客户、不是 paid 状态
     *    - 校验核销金额不超过欠款金额
     *    - 计算新的收款状态和金额
     * 4. 校验核销总额 <= 收款金额
     * 5. 生成收款流水号（SK+日期+4位序号）
     * 6. 插入 financial_ledgers 记录（含 writeOffDetails JSON）
     * 7. 逐笔更新 sales_orders：paymentStatus、paidAmountFen、debtAmountFen
     * 8. 更新 customers：totalDebtFen、totalPaidFen
     * 
     * 全程在 @Transactional 事务中执行，保证数据一致性
     * 
     * @param req 收款请求
     * @return 生成的财务流水记录
     */
    FinancialLedger receive(FinanceReceiveRequest req);

    /**
     * 分页查询财务流水
     * 
     * 按 transactionTime 倒序排列
     * 
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     * @return 分页的财务流水列表
     */
    PageResponse<FinancialLedger> ledgers(int page, int pageSize);
}
