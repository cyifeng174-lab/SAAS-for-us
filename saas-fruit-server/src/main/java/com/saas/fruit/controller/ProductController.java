package com.saas.fruit.controller;

import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.SpecManageRequest;
import com.saas.fruit.entity.ProductCategory;
import com.saas.fruit.service.SpecService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 商品种类管理控制器
 * 负责商品种类的增删改查
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private SpecService specService;

    /**
     * 查询商品种类列表
     */
    @GetMapping
    public ApiResponse<PageResponse<ProductCategory>> listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        log.info("[商品列表] 关键词={}, 页码={}", keyword, page);

        PageResponse<ProductCategory> result = specService.listProducts(keyword, page, pageSize);
        return ApiResponse.success(result);
    }

    /**
     * 新增商品种类
     */
    @PostMapping
    public ApiResponse<ProductCategory> addProduct(@RequestBody SpecManageRequest req) {
        log.info("[商品创建] 名称={}", req.getName());
        ProductCategory product = specService.addProduct(req);
        return ApiResponse.success(product, "商品添加成功");
    }
}
