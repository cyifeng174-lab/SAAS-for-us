package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售订单明细实体
 */
@Data
@TableName("sales_order_items")
public class SalesOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 库存ID */
    private Long inventoryId;

    /** 商品名称 */
    private String productName;

    /** 等级 */
    private String grade;

    /** 规格 */
    private String spec;

    /** 重量（斤） */
    private BigDecimal weightJin;

    /** 单价（分/斤） */
    private Integer unitPriceFen;

    /** 小计金额（分） */
    private Integer subtotalFen;

    /** 成本单价（分/斤，FIFO） */
    private Integer costPriceFen;

    /** 总成本（分） */
    private Integer costTotalFen;

    /** 利润（分） */
    private Integer profitFen;

    /** 出库批次ID */
    private String batchId;

    /** 出库批次号 */
    private String batchNo;

    /** 排序 */
    private Integer sortOrder;
}
