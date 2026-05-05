package com.saas.fruit.service;

import com.saas.fruit.dto.request.ProcessingCreateRequest;
import com.saas.fruit.entity.ProcessingOrder;

/**
 * 加工服务接口
 * 负责加工订单的创建、作废等业务逻辑
 * 
 * 将采购的原果加工为成品，记录成本投入和产出明细，
 * 产出成品自动入库（生成/更新 inventories 记录）
 */
public interface ProcessingService {

    /**
     * 创建加工订单（加工入库）
     * 
     * 核心流程：
     * 1. 入参校验（purchaseId、outputs 不为空，每个产出项的 productName、weightJin>0）
     * 2. 查询采购单，校验存在且状态不是 completed/cancelled
     * 3. 读取采购成本与投入重量
     * 4. 计算加工总成本（人工+包装+运输+其他）
     * 5. 计算总成本 = 采购成本 + 加工总成本
     * 6. 按重量比例分摊成本到每个产出品（最后一个用差额法补齐）
     * 7. 生成加工单号（JG+日期+4位序号）和批次ID
     * 8. 插入 processing_orders 记录
     * 9. 遍历产出品，更新/创建 inventories 库存记录（移动加权平均成本）
     * 10. 回填 output_products 中的 inventory_id
     * 11. 更新采购单状态为 completed
     * 
     * 全程在 @Transactional 事务中执行，保证数据一致性
     * 
     * @param req 加工单创建请求
     * @return 创建成功的加工单对象
     */
    ProcessingOrder create(ProcessingCreateRequest req);

    /**
     * 作废加工单
     * 
     * 仅允许状态不是 completed/cancelled 的订单执行作废操作
     * 作废后将状态改为 cancelled，记录作废时间
     * 
     * @param orderId 加工单ID
     */
    void cancel(Long orderId);
}
