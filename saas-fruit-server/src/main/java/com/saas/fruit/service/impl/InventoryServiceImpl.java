package com.saas.fruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.entity.Inventory;
import com.saas.fruit.mapper.InventoryMapper;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 库存管理服务实现类
 * 负责库存列表的分页查询、关键词模糊搜索、多租户数据隔离
 */
@Slf4j
@Service
public class InventoryServiceImpl implements InventoryService {

    @Autowired
    private InventoryMapper inventoryMapper;

    /**
     * 分页查询库存列表
     * 支持关键词模糊搜索 productName/fruitName/grade/spec 四个字段
     * 按当前租户ID过滤数据，实现多租户数据隔离
     */
    @Override
    public PageResponse<Inventory> list(String keyword, int page, int pageSize) {

        // 获取当前租户ID，确保数据隔离
        String tenantId = LoginUserContext.getTenantId();

        // 构建查询条件
        LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();

        // 多租户数据隔离：只查询当前租户的库存记录
        queryWrapper.eq(Inventory::getTenantId, tenantId);

        // 关键词模糊搜索：同时搜索成品名称/水果名称/等级/规格
        // 只要任一字段包含关键词即可匹配
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Inventory::getProductName, keyword)
                    .or()
                    .like(Inventory::getFruitName, keyword)
                    .or()
                    .like(Inventory::getSpec, keyword)
                    .or()
                    .like(Inventory::getGrade, keyword));
        }

        // 按创建时间倒序排列（最新入库的在前）
        queryWrapper.orderByDesc(Inventory::getCreateTime);

        // 执行分页查询（MyBatis-Plus Page页码从1开始，前端传入的page是0-based）
        Page<Inventory> pageResult = inventoryMapper.selectPage(
                new Page<>(page + 1, pageSize),
                queryWrapper
        );

        log.debug("[库存列表] 租户={}, 关键词={}, 查询结果总数={}, 当前页={}",
                tenantId, keyword, pageResult.getTotal(), page);

        // 构建分页响应
        return PageResponse.success(
                pageResult.getRecords(),
                pageResult.getTotal(),
                page,
                pageSize
        );
    }
}
