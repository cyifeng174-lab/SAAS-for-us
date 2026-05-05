package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存实体
 */
@Data
@TableName("inventories")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 成品名称 */
    private String productName;

    /** 水果名称 */
    private String fruitName;

    /** 等级 */
    private String grade;

    /** 规格 */
    private String spec;

    /** 产地 */
    private String origin;

    /** 当前库存（斤） */
    private BigDecimal currentStockJin;

    /** 加权平均成本（分/斤） */
    private Integer unitCostFen;

    /** 库存总成本（分） */
    private Integer totalCostFen;

    /** 建议售价（分/斤） */
    private Integer suggestedPriceFen;

    /** 来源批次信息（JSON） */
    private String batchList;

    /** 累计入库（斤） */
    private BigDecimal totalInboundJin;

    /** 累计出库（斤） */
    private BigDecimal totalOutboundJin;

    /** 预警库存量（斤） */
    private BigDecimal warningStockJin;

    /** 状态：normal/low_stock/out_of_stock */
    private String status;

    /** 最近入库时间戳 */
    private Long lastInboundTime;

    /** 最近出库时间戳 */
    private Long lastOutboundTime;

    /** 备注 */
    private String remark;

    /** 创建时间戳 */
    private Long createTime;

    /** 更新时间戳 */
    private Long updateTime;
}
