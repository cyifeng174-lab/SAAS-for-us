package com.saas.fruit.service;

import com.saas.fruit.common.PageResponse;
import com.saas.fruit.entity.Inventory;

/**
 * 库存管理服务接口
 * 负责库存列表查询、多条件模糊搜索、分页展示
 */
public interface InventoryService {

    /**
     * 分页查询库存列表
     * 支持关键词模糊搜索（productName/fruitName/grade/spec），按当前租户过滤
     *
     * @param keyword  搜索关键词（模糊匹配成品名称/水果名称/等级/规格）
     * @param page     页码（从0开始）
     * @param pageSize 每页条数
     * @return 分页响应，包含库存列表及分页信息
     */
    PageResponse<Inventory> list(String keyword, int page, int pageSize);
}
