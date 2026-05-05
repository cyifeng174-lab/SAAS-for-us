package com.saas.fruit.controller;

import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.PurchaseCreateRequest;
import com.saas.fruit.entity.Purchase;
import com.saas.fruit.service.PurchaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 采购管理控制器
 * 负责采购单的查询、创建和作废
 */
@Slf4j
@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    @Autowired
    private PurchaseService purchaseService;

    /**
     * 查询采购单列表（分页+筛选）
     */
    @GetMapping
    public ApiResponse<PageResponse<Purchase>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        log.info("[采购列表] keyword={}, status={}, supplierId={}, page={}", keyword, status, supplierId, page);
        PageResponse<Purchase> result = purchaseService.list(keyword, status, supplierId, page, pageSize);
        return ApiResponse.success(result);
    }

    /**
     * 创建采购单
     */
    @PostMapping
    public ApiResponse<Purchase> create(@RequestBody PurchaseCreateRequest req) {
        log.info("[采购创建] 供应商ID={}, 原果={}, 重量={}斤, 总金额={}分",
                req.getSupplierId(), req.getFruitName(), req.getWeightJin(), req.getTotalAmountFen());

        Purchase purchase = purchaseService.create(req);
        return ApiResponse.success(purchase, "采购单创建成功");
    }

    /**
     * 作废采购单
     * 仅允许状态为pending（待加工）的订单作废
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        log.info("[采购作废] 采购单ID={}", id);
        purchaseService.cancel(id);
        return ApiResponse.success(null, "采购单已作废");
    }
}
