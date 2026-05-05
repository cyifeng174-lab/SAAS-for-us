package com.saas.fruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.fruit.common.BusinessException;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.CustomerCreateRequest;
import com.saas.fruit.dto.request.CustomerUpdateRequest;
import com.saas.fruit.dto.response.CustomerDetailResponse;
import com.saas.fruit.entity.Customer;
import com.saas.fruit.entity.SalesOrder;
import com.saas.fruit.mapper.CustomerMapper;
import com.saas.fruit.mapper.SalesOrderMapper;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.service.CustomerService;
import com.saas.fruit.utils.BatchNoGenerator;
import com.saas.fruit.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 客户管理服务实现类
 * 负责客户的增删改查、搜索筛选、分页列表以及详情查看
 * 所有操作均在当前租户上下文下进行，确保多租户数据隔离
 */
@Slf4j
@Service
public class CustomerServiceImpl implements CustomerService {

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private BatchNoGenerator batchNoGenerator;

    /**
     * 分页查询客户列表
     * 支持关键词搜索（名称/电话）、客户类型筛选、欠款状态筛选、状态筛选、多字段排序
     */
    @Override
    public PageResponse<Customer> list(String keyword, String customerType, String debtStatus,
                                        String status, int page, int pageSize,
                                        String orderByField, String orderByDirection) {

        // 获取当前租户ID，确保数据隔离
        String tenantId = LoginUserContext.getTenantId();

        // 构建查询条件
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();

        // 多租户数据隔离：只查询当前租户的客户
        queryWrapper.eq(Customer::getTenantId, tenantId);

        // 排除已软删除的客户（status="deleted"的记录不显示）
        queryWrapper.ne(Customer::getStatus, "deleted");

        // 关键词搜索：模糊匹配客户名称和联系电话
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Customer::getName, keyword)
                    .or()
                    .like(Customer::getPhone, keyword));
        }

        // 客户类型筛选：wholesale/retail/both
        if (StringUtils.hasText(customerType)) {
            queryWrapper.eq(Customer::getCustomerType, customerType);
        }

        // 欠款状态筛选：in_debt=有欠款(totalDebtFen>0), no_debt=无欠款
        if (StringUtils.hasText(debtStatus)) {
            if ("in_debt".equals(debtStatus)) {
                // 有欠款：欠款金额大于0
                queryWrapper.gt(Customer::getTotalDebtFen, 0);
            } else if ("no_debt".equals(debtStatus)) {
                // 无欠款：欠款金额等于0或为null
                queryWrapper.and(wrapper -> wrapper
                        .eq(Customer::getTotalDebtFen, 0)
                        .or()
                        .isNull(Customer::getTotalDebtFen));
            }
        }

        // 客户状态筛选：active/inactive/blacklist
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Customer::getStatus, status);
        }

        // 动态排序：根据前端传入的字段名和方向排序
        boolean isAsc = "asc".equalsIgnoreCase(orderByDirection);
        queryWrapper.orderBy(true, isAsc, resolveOrderColumn(orderByField));

        // 执行分页查询
        Page<Customer> pageResult = customerMapper.selectPage(
                new Page<>(page + 1, pageSize),  // MyBatis-Plus Page页码从1开始，前端传入的是0-based
                queryWrapper
        );

        log.debug("[客户列表] 租户={}, 查询结果总数={}, 当前页={}", tenantId, pageResult.getTotal(), page);

        // 构建分页响应
        return PageResponse.success(
                pageResult.getRecords(),
                pageResult.getTotal(),
                page,
                pageSize
        );
    }

    /**
     * 查看客户详情
     * 返回客户基本信息 + 最近10条销售订单
     */
    @Override
    public CustomerDetailResponse detail(Long id) {
        String tenantId = LoginUserContext.getTenantId();

        // 查询客户信息，校验客户存在且属于当前租户
        Customer customer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getId, id)
                        .eq(Customer::getTenantId, tenantId)
                        .ne(Customer::getStatus, "deleted")
        );

        if (customer == null) {
            throw new BusinessException(404, "客户不存在或已被删除");
        }

        // 查询该客户最近10条销售订单（按创建时间倒序）
        List<SalesOrder> recentOrders = salesOrderMapper.selectList(
                new LambdaQueryWrapper<SalesOrder>()
                        .eq(SalesOrder::getCustomerId, id)
                        .eq(SalesOrder::getTenantId, tenantId)
                        .orderByDesc(SalesOrder::getCreateTime)
                        .last("LIMIT 10")
        );

        log.debug("[客户详情] 客户ID={}, 租户={}, 最近订单数={}", id, tenantId, recentOrders.size());

        return new CustomerDetailResponse(customer, recentOrders);
    }

    /**
     * 创建新客户
     * 校验名称和电话在当前租户下的唯一性，自动生成客户编号，设置初始状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Customer create(CustomerCreateRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        // ========== 校验客户名称唯一性（同租户下不可重复） ==========
        Long nameCount = customerMapper.selectCount(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getTenantId, tenantId)
                        .eq(Customer::getName, req.getName())
                        .ne(Customer::getStatus, "deleted")
        );
        if (nameCount > 0) {
            throw new BusinessException(400, "客户名称【" + req.getName() + "】已存在，请更换名称");
        }

        // ========== 校验联系电话唯一性 ==========
        if (StringUtils.hasText(req.getPhone())) {
            Long phoneCount = customerMapper.selectCount(
                    new LambdaQueryWrapper<Customer>()
                            .eq(Customer::getTenantId, tenantId)
                            .eq(Customer::getPhone, req.getPhone())
                            .ne(Customer::getStatus, "deleted")
            );
            if (phoneCount > 0) {
                throw new BusinessException(400, "联系电话【" + req.getPhone() + "】已被其他客户使用");
            }
        }

        // ========== 生成客户编号：KH + yyyyMMdd + 4位序号 ==========
        // 序号基于今日已创建的客户数 + 1
        long[] todayRange = DateUtil.getTodayRange();
        long todayCount = customerMapper.selectCount(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getTenantId, tenantId)
                        .ge(Customer::getCreateTime, todayRange[0])
                        .le(Customer::getCreateTime, todayRange[1])
        );
        String customerNo = batchNoGenerator.genCustomerNo(todayCount + 1);

        // ========== 构建并插入客户实体 ==========
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setCustomerNo(customerNo);
        customer.setName(req.getName());
        customer.setPhone(req.getPhone());
        customer.setAddress(req.getAddress());
        customer.setCustomerType(req.getCustomerType() != null ? req.getCustomerType() : "both");
        customer.setCreditLimitFen(req.getCreditLimitFen() != null ? req.getCreditLimitFen() : 0);
        // 新客户初始财务数据
        customer.setTotalDebtFen(0);
        customer.setTotalSalesFen(0);
        customer.setTotalPaidFen(0);
        customer.setOrderCount(0);
        customer.setStatus("active");
        customer.setRemark(req.getRemark());
        customer.setCreateTime(now);
        customer.setUpdateTime(now);

        customerMapper.insert(customer);
        log.info("[客户创建] 客户编号={}, 名称={}, 租户={}", customerNo, req.getName(), tenantId);

        return customer;
    }

    /**
     * 更新客户信息
     * 校验客户存在性和归属，更新名称、电话、地址、备注等基本信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerUpdateRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        // ========== 校验客户是否存在且属于当前租户 ==========
        Customer existing = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getId, req.getCustomerId())
                        .eq(Customer::getTenantId, tenantId)
                        .ne(Customer::getStatus, "deleted")
        );

        if (existing == null) {
            throw new BusinessException(404, "客户不存在或已被删除");
        }

        // ========== 校验名称唯一性（排除自身） ==========
        if (StringUtils.hasText(req.getName()) && !req.getName().equals(existing.getName())) {
            Long nameCount = customerMapper.selectCount(
                    new LambdaQueryWrapper<Customer>()
                            .eq(Customer::getTenantId, tenantId)
                            .eq(Customer::getName, req.getName())
                            .ne(Customer::getStatus, "deleted")
                            .ne(Customer::getId, req.getCustomerId())
            );
            if (nameCount > 0) {
                throw new BusinessException(400, "客户名称【" + req.getName() + "】已被其他客户使用");
            }
        }

        // ========== 校验电话唯一性（排除自身） ==========
        if (StringUtils.hasText(req.getPhone()) && !req.getPhone().equals(existing.getPhone())) {
            Long phoneCount = customerMapper.selectCount(
                    new LambdaQueryWrapper<Customer>()
                            .eq(Customer::getTenantId, tenantId)
                            .eq(Customer::getPhone, req.getPhone())
                            .ne(Customer::getStatus, "deleted")
                            .ne(Customer::getId, req.getCustomerId())
            );
            if (phoneCount > 0) {
                throw new BusinessException(400, "联系电话【" + req.getPhone() + "】已被其他客户使用");
            }
        }

        // ========== 更新客户信息（只更新允许修改的字段） ==========
        existing.setName(req.getName());
        existing.setPhone(req.getPhone());
        existing.setAddress(req.getAddress());
        existing.setRemark(req.getRemark());
        existing.setUpdateTime(now);

        customerMapper.updateById(existing);
        log.info("[客户更新] 客户ID={}, 名称={}, 租户={}", req.getCustomerId(), req.getName(), tenantId);
    }

    /**
     * 删除客户（软删除）
     * 将客户状态标记为deleted，删除前检查是否有未结清欠款，
     * 有欠款的客户不允许删除
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        // ========== 校验客户是否存在且属于当前租户 ==========
        Customer customer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getId, id)
                        .eq(Customer::getTenantId, tenantId)
                        .ne(Customer::getStatus, "deleted")
        );

        if (customer == null) {
            throw new BusinessException(404, "客户不存在或已被删除");
        }

        // ========== 检查是否有未结清欠款 ==========
        if (customer.getTotalDebtFen() != null && customer.getTotalDebtFen() > 0) {
            throw new BusinessException(400,
                    "该客户尚有欠款未结清（" + (customer.getTotalDebtFen() / 100.0) + "元），请先结清欠款后再删除");
        }

        // ========== 执行软删除 ==========
        customer.setStatus("deleted");
        customer.setDeleteTime(now);
        customer.setUpdateTime(now);

        customerMapper.updateById(customer);
        log.info("[客户删除] 客户ID={}, 名称={}, 租户={}", id, customer.getName(), tenantId);
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 将前端传入的排序字段名映射为Lambda表达式的getter方法引用
     * 支持白名单校验，防止SQL注入或非法字段排序
     *
     * @param field 前端传入的排序字段名（驼峰命名，如 name, createTime）
     * @return 对应的SFunction getter引用，默认按创建时间倒序
     */
    private SFunction<Customer, ?> resolveOrderColumn(String field) {
        if (!StringUtils.hasText(field)) {
            return Customer::getCreateTime;
        }
        switch (field) {
            case "name":
                return Customer::getName;
            case "phone":
                return Customer::getPhone;
            case "customerType":
                return Customer::getCustomerType;
            case "creditLimitFen":
                return Customer::getCreditLimitFen;
            case "totalDebtFen":
                return Customer::getTotalDebtFen;
            case "totalSalesFen":
                return Customer::getTotalSalesFen;
            case "totalPaidFen":
                return Customer::getTotalPaidFen;
            case "orderCount":
                return Customer::getOrderCount;
            case "lastOrderTime":
                return Customer::getLastOrderTime;
            case "firstOrderTime":
                return Customer::getFirstOrderTime;
            case "createTime":
                return Customer::getCreateTime;
            case "updateTime":
                return Customer::getUpdateTime;
            default:
                // 非法排序字段，默认按创建时间倒序
                log.warn("[客户列表] 非法的排序字段: {}, 使用默认排序 createTime desc", field);
                return Customer::getCreateTime;
        }
    }
}
