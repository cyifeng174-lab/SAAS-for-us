package com.saas.fruit.service;

import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.PurchaseCreateRequest;
import com.saas.fruit.entity.Purchase;

/**
 * 采购单服务接口
 * 负责采购单的创建、作废、查询等业务逻辑
 */
public interface PurchaseService {

    /**
     * 分页查询采购单列表
     * @param keyword 关键词搜索（批次号/原果名称/供应商名称）
     * @param status 状态筛选：pending/processing/completed/cancelled
     * @param supplierId 供应商ID筛选
     * @param page 页码（0-based）
     * @param pageSize 每页条数
     * @return 分页数据
     */
    PageResponse<Purchase> list(String keyword, String status, Long supplierId, int page, int pageSize);

    /**
     * 创建采购单
     * 
     * 核心流程：
     * 1. 入参校验
     * 2. 校验供应商存在且状态有效
     * 3. 生成采购批次号
     * 4. 插入采购单记录
     * 5. 更新供应商统计信息
     * 6. 生成财务流水（全额应付 + 付款核销）
     * 
     * 全程在 @Transactional 事务中执行，保证数据一致性
     * 
     * @param req 采购单创建请求
     * @return 创建成功的采购单对象
     */
    Purchase create(PurchaseCreateRequest req);

    /**
     * 作废采购单
     * 
     * 仅允许状态为 pending（待加工）的订单执行作废操作
     * 作废后将状态改为 cancelled，记录作废时间
     * 
     * @param orderId 采购单ID
     */
    void cancel(Long orderId);
}
