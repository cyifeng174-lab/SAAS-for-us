package com.saas.fruit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.SalesOrderCreateRequest;
import com.saas.fruit.entity.SalesOrder;
import com.saas.fruit.mapper.SalesOrderMapper;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.service.SalesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售管理控制器
 * 负责销售开单和历史订单查询
 */
@Slf4j
@RestController
@RequestMapping("/api/sales")
public class SalesController {

    @Autowired
    private SalesService salesService;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    /**
     * 创建销售订单（销售开单）
     * 包含FIFO库存扣减、客户欠款更新、财务流水生成
     */
    @PostMapping
    public ApiResponse<SalesOrder> create(@RequestBody SalesOrderCreateRequest req) {
        log.info("[销售开单] 客户ID={}, 订单项数量={}", req.getCustomerId(),
                req.getOrderItems() != null ? req.getOrderItems().size() : 0);

        SalesOrder salesOrder = salesService.create(req);
        return ApiResponse.success(salesOrder, "销售开单成功");
    }

    /**
     * 查询销售历史订单（分页）
     * 按创建时间倒序排列
     */
    @GetMapping("/history")
    public ApiResponse<PageResponse<SalesOrder>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        String tenantId = LoginUserContext.getTenantId();

        log.info("[销售历史] 租户={}, 页码={}", tenantId, page);

        LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SalesOrder::getTenantId, tenantId);
        queryWrapper.orderByDesc(SalesOrder::getCreateTime);

        Page<SalesOrder> pageResult = salesOrderMapper.selectPage(
                new Page<>(page + 1, pageSize),
                queryWrapper
        );

        PageResponse<SalesOrder> result = PageResponse.success(
                pageResult.getRecords(),
                pageResult.getTotal(),
                page,
                pageSize
        );

        return ApiResponse.success(result);
    }

    /**
     * 作废销售订单
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        log.info("[销售作废] 订单ID={}", id);
        salesService.cancel(id);
        return ApiResponse.success(null, "订单已作废");
    }
}
