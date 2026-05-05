package com.saas.fruit.controller;

import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.SupplierCreateRequest;
import com.saas.fruit.dto.request.SupplierUpdateRequest;
import com.saas.fruit.entity.Supplier;
import com.saas.fruit.service.SupplierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 供应商管理控制器
 * 负责供应商的增删改查、列表搜索、状态切换
 */
@Slf4j
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    @Autowired
    private SupplierService supplierService;

    /**
     * 分页查询供应商列表
     * 支持关键词搜索和状态筛选
     */
    @GetMapping
    public ApiResponse<PageResponse<Supplier>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        log.info("[供应商列表] 关键词={}, 状态={}, 页码={}", keyword, status, page);

        PageResponse<Supplier> result = supplierService.list(keyword, status, page, pageSize);
        return ApiResponse.success(result);
    }

    /**
     * 查询供应商详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Supplier> detail(@PathVariable Long id) {
        log.info("[供应商详情] 供应商ID={}", id);
        Supplier supplier = supplierService.detail(id);
        return ApiResponse.success(supplier);
    }

    /**
     * 创建新供应商
     */
    @PostMapping
    public ApiResponse<Supplier> create(@RequestBody SupplierCreateRequest req) {
        log.info("[供应商创建] 名称={}", req.getName());
        Supplier supplier = supplierService.create(req);
        return ApiResponse.success(supplier, "供应商创建成功");
    }

    /**
     * 更新供应商信息
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody SupplierUpdateRequest req) {
        log.info("[供应商更新] 供应商ID={}", id);
        req.setSupplierId(id);
        supplierService.update(req);
        return ApiResponse.success(null, "供应商信息更新成功");
    }

    /**
     * 删除供应商（软删除）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("[供应商删除] 供应商ID={}", id);
        supplierService.delete(id);
        return ApiResponse.success(null, "供应商已删除");
    }

    /**
     * 切换供应商启用/停用状态
     */
    @PostMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id) {
        log.info("[供应商状态切换] 供应商ID={}", id);
        supplierService.toggleStatus(id);
        return ApiResponse.success(null, "供应商状态已切换");
    }
}
