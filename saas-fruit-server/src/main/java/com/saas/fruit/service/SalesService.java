package com.saas.fruit.service;

import com.saas.fruit.dto.request.SalesOrderCreateRequest;
import com.saas.fruit.entity.SalesOrder;

/**
 * 销售单服务接口
 * 负责销售开单、FIFO 库存扣减等业务逻辑
 */
public interface SalesService {

    /**
     * 创建销售订单（销售开单）
     * 
     * 核心流程：
     * 1. 入参校验
     * 2. 校验客户存在且状态有效（inactive/blacklist 禁止）
     * 3. 行级锁查询库存，校验库存充足
     * 4. FIFO（先进先出）扣减批次库存，计算真实成本
     * 5. 计算总金额、收款状态、欠款金额
     * 6. 生成销售单号并插入 sales_orders 和 sales_order_items
     * 7. 更新库存记录（currentStockJin、batch_list、totalOutboundJin等）
     * 8. 更新客户统计信息
     * 9. 生成财务流水（全额应收 + 收款核销）
     * 
     * 全程在 @Transactional 事务中执行，保证数据一致性
     * FIFO 扣减时使用 SELECT ... FOR UPDATE 行级锁防止并发超卖
     * 
     * @param req 销售开单请求
     * @return 创建成功的销售订单（含明细）
     */
    SalesOrder create(SalesOrderCreateRequest req);

    /**
     * 作废销售订单
     * 仅允许状态为 completed 且未完全收款的订单作废
     * @param id 订单ID
     */
    void cancel(Long id);
}
