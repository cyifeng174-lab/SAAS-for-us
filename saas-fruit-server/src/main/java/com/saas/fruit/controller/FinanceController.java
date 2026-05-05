package com.saas.fruit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.common.BusinessException;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.FinanceReceiveRequest;
import com.saas.fruit.entity.Customer;
import com.saas.fruit.entity.FinancialLedger;
import com.saas.fruit.mapper.CustomerMapper;
import com.saas.fruit.mapper.FinancialLedgerMapper;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 财务管理控制器
 * 负责客户收款、财务流水查询
 */
@Slf4j
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private FinancialLedgerMapper financialLedgerMapper;

    /**
     * 客户收款
     * 记录客户的回款操作，更新客户欠款余额，生成收款流水
     * TODO: 实现完整的收款逻辑 - 支持核销多笔订单
     */
    @PostMapping("/receive")
    public ApiResponse<Void> receive(@RequestBody FinanceReceiveRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        log.info("[客户收款] 客户ID={}, 金额={}分, 方式={}",
                req.getCustomerId(), req.getAmountFen(), req.getPaymentMethod());

        // ========== 1. 校验客户存在 ==========
        Customer customer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getId, req.getCustomerId())
                        .eq(Customer::getTenantId, tenantId)
                        .ne(Customer::getStatus, "deleted")
        );
        if (customer == null) {
            throw new BusinessException(404, "客户不存在");
        }

        // ========== 2. 计算新的欠款余额 ==========
        int currentDebtFen = customer.getTotalDebtFen() != null ? customer.getTotalDebtFen() : 0;
        int newDebtFen = currentDebtFen - req.getAmountFen();
        if (newDebtFen < 0) {
            // 支付金额超过欠款，允许但欠款归零
            newDebtFen = 0;
        }

        // ========== 3. 更新客户欠款和收款统计 ==========
        customerMapper.updateReceiveStats(
                req.getCustomerId(),
                newDebtFen,
                req.getAmountFen(),
                now
        );

        log.info("[客户收款] 客户ID={}, 收款前欠款={}分, 收款金额={}分, 收款后欠款={}分",
                req.getCustomerId(), currentDebtFen, req.getAmountFen(), newDebtFen);

        // TODO: 生成收款财务流水（financial_ledgers记录）
        // TODO: 处理核销明细（writeOffDetails），更新对应订单的收款状态

        return ApiResponse.success(null, "收款成功");
    }

    /**
     * 查询财务流水（分页）
     * 按交易时间倒序排列
     */
    @GetMapping("/ledgers")
    public ApiResponse<PageResponse<FinancialLedger>> ledgers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        String tenantId = LoginUserContext.getTenantId();

        log.info("[财务流水] 租户={}, 页码={}", tenantId, page);

        LambdaQueryWrapper<FinancialLedger> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinancialLedger::getTenantId, tenantId);
        queryWrapper.orderByDesc(FinancialLedger::getCreateTime);

        Page<FinancialLedger> pageResult = financialLedgerMapper.selectPage(
                new Page<>(page + 1, pageSize),
                queryWrapper
        );

        PageResponse<FinancialLedger> result = PageResponse.success(
                pageResult.getRecords(),
                pageResult.getTotal(),
                page,
                pageSize
        );

        return ApiResponse.success(result);
    }
}
