package com.saas.fruit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.ProcessingCreateRequest;
import com.saas.fruit.entity.ProcessingOrder;
import com.saas.fruit.mapper.ProcessingOrderMapper;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.service.ProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 加工管理控制器
 * 负责加工订单的创建、作废和历史查询
 */
@Slf4j
@RestController
@RequestMapping("/api/processing")
public class ProcessingController {

    @Autowired
    private ProcessingService processingService;

    @Autowired
    private ProcessingOrderMapper processingOrderMapper;

    /**
     * 创建加工订单
     */
    @PostMapping
    public ApiResponse<ProcessingOrder> create(@RequestBody ProcessingCreateRequest req) {
        log.info("[加工创建] 采购单ID={}, 产成品数量={}",
                req.getPurchaseId(),
                req.getOutputs() != null ? req.getOutputs().size() : 0);

        ProcessingOrder order = processingService.create(req);
        return ApiResponse.success(order, "加工入库成功");
    }

    /**
     * 作废加工订单
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        log.info("[加工作废] 加工单ID={}", id);
        processingService.cancel(id);
        return ApiResponse.success(null, "加工单已作废");
    }

    /**
     * 查询加工历史订单（分页）
     */
    @GetMapping("/history")
    public ApiResponse<PageResponse<ProcessingOrder>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        String tenantId = LoginUserContext.getTenantId();

        log.info("[加工历史] 租户={}, 页码={}", tenantId, page);

        LambdaQueryWrapper<ProcessingOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProcessingOrder::getTenantId, tenantId);
        queryWrapper.orderByDesc(ProcessingOrder::getCreateTime);

        Page<ProcessingOrder> pageResult = processingOrderMapper.selectPage(
                new Page<>(page + 1, pageSize),
                queryWrapper
        );

        PageResponse<ProcessingOrder> result = PageResponse.success(
                pageResult.getRecords(),
                pageResult.getTotal(),
                page,
                pageSize
        );

        return ApiResponse.success(result);
    }
}
