package com.saas.fruit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.common.BusinessException;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.entity.Inventory;
import com.saas.fruit.mapper.InventoryMapper;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 库存管理控制器
 * 负责库存列表查询和单条库存详情查看
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryMapper inventoryMapper;

    /**
     * 分页查询库存列表
     * 支持关键词模糊搜索（成品名称/水果名称/等级/规格）
     */
    @GetMapping
    public ApiResponse<PageResponse<Inventory>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        log.info("[库存列表] 关键词={}, 页码={}", keyword, page);

        PageResponse<Inventory> result = inventoryService.list(keyword, page, pageSize);
        return ApiResponse.success(result);
    }

    /**
     * 查询单条库存详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Inventory> detail(@PathVariable Long id) {
        String tenantId = LoginUserContext.getTenantId();

        log.info("[库存详情] 库存ID={}, 租户={}", id, tenantId);

        Inventory inventory = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getId, id)
                        .eq(Inventory::getTenantId, tenantId)
        );

        if (inventory == null) {
            throw new BusinessException(404, "库存记录不存在");
        }

        return ApiResponse.success(inventory);
    }
}
