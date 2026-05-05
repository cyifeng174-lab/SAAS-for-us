package com.saas.fruit.controller;

import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.CustomerCreateRequest;
import com.saas.fruit.dto.request.CustomerUpdateRequest;
import com.saas.fruit.dto.response.CustomerDetailResponse;
import com.saas.fruit.entity.Customer;
import com.saas.fruit.service.CustomerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 客户管理控制器
 * 负责客户的增删改查、搜索筛选和详情查看
 */
@Slf4j
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    /**
     * 分页查询客户列表
     * 支持关键词搜索、按客户类型/欠款状态/状态筛选、多字段排序
     */
    @GetMapping
    public ApiResponse<PageResponse<Customer>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String debtStatus,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String orderByField,
            @RequestParam(required = false) String orderByDirection) {

        log.info("[客户列表] 关键词={}, 客户类型={}, 欠款状态={}, 状态={}, 页码={}",
                keyword, customerType, debtStatus, status, page);

        PageResponse<Customer> result = customerService.list(
                keyword, customerType, debtStatus, status, page, pageSize,
                orderByField, orderByDirection);

        return ApiResponse.success(result);
    }

    /**
     * 查询客户详情
     * 返回客户基本信息及其最近10条销售订单
     */
    @GetMapping("/{id}")
    public ApiResponse<CustomerDetailResponse> detail(@PathVariable Long id) {
        log.info("[客户详情] 客户ID={}", id);
        CustomerDetailResponse response = customerService.detail(id);
        return ApiResponse.success(response);
    }

    /**
     * 创建新客户
     */
    @PostMapping
    public ApiResponse<Customer> create(@RequestBody CustomerCreateRequest req) {
        log.info("[客户创建] 名称={}", req.getName());
        Customer customer = customerService.create(req);
        return ApiResponse.success(customer, "客户创建成功");
    }

    /**
     * 更新客户信息
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody CustomerUpdateRequest req) {
        log.info("[客户更新] 客户ID={}", id);
        req.setCustomerId(id);
        customerService.update(req);
        return ApiResponse.success(null, "客户信息更新成功");
    }

    /**
     * 删除客户（软删除）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("[客户删除] 客户ID={}", id);
        customerService.delete(id);
        return ApiResponse.success(null, "客户已删除");
    }
}
