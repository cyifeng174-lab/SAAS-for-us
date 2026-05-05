package com.saas.fruit.service;

import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.CustomerCreateRequest;
import com.saas.fruit.dto.request.CustomerUpdateRequest;
import com.saas.fruit.dto.response.CustomerDetailResponse;
import com.saas.fruit.entity.Customer;

/**
 * 客户管理服务接口
 * 负责客户的增删改查、搜索筛选、分页列表以及详情查看
 */
public interface CustomerService {

    /**
     * 分页查询客户列表
     * 支持关键词搜索、按客户类型/欠款状态/状态筛选，以及多字段排序
     *
     * @param keyword          搜索关键词（模糊匹配客户名称和联系电话）
     * @param customerType     客户类型筛选：wholesale/retail/both，为null则不过滤
     * @param debtStatus       欠款状态筛选：in_debt（有欠款）/no_debt（无欠款），为null则不过滤
     * @param status           客户状态筛选：active/inactive/blacklist，为null则不过滤（不含deleted）
     * @param page             页码（从0开始）
     * @param pageSize         每页条数
     * @param orderByField     排序字段名（如 name, createTime, totalDebtFen）
     * @param orderByDirection 排序方向：asc/desc
     * @return 分页响应，包含客户列表及分页信息
     */
    PageResponse<Customer> list(String keyword, String customerType, String debtStatus,
                                 String status, int page, int pageSize,
                                 String orderByField, String orderByDirection);

    /**
     * 查看客户详情
     * 返回客户基本信息及其最近10条销售订单
     *
     * @param id 客户ID
     * @return 客户详情响应，包含客户信息与最近订单
     */
    CustomerDetailResponse detail(Long id);

    /**
     * 创建新客户
     * 自动生成客户编号（KH+日期+4位序号），校验名称和电话在当前租户下的唯一性
     *
     * @param req 客户创建请求
     * @return 创建成功的客户实体
     */
    Customer create(CustomerCreateRequest req);

    /**
     * 更新客户信息
     * 校验客户是否存在，更新客户名称、电话、地址、备注等基本信息
     *
     * @param req 客户更新请求
     */
    void update(CustomerUpdateRequest req);

    /**
     * 删除客户（软删除）
     * 将客户状态标记为deleted，删除前检查是否有未结清欠款
     *
     * @param id 客户ID
     */
    void delete(Long id);
}
