package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 加工订单实体
 */
@Data
@TableName("processing_orders")
public class ProcessingOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 加工单号 */
    private String orderNo;

    /** 来源采购单ID */
    private Long purchaseId;

    /** 来源采购批次号 */
    private String purchaseBatchNo;

    /** 原果名称 */
    private String fruitName;

    /** 原果投入重量（斤） */
    private BigDecimal inputWeightJin;

    /** 人工费用（分） */
    private Integer laborCostFen;

    /** 包装费用（分） */
    private Integer packagingCostFen;

    /** 运输费用（分） */
    private Integer transportCostFen;

    /** 其他费用（分） */
    private Integer otherCostFen;

    /** 加工总成本（分） */
    private Integer totalProcessingCostFen;

    /** 采购成本（分） */
    private Integer purchaseCostFen;

    /** 总成本（分） */
    private Integer totalCostFen;

    /** 产出商品列表（JSON） */
    private String outputProducts;

    /** 总产出重量（斤） */
    private BigDecimal totalOutputWeightJin;

    /** 损耗重量（斤） */
    private BigDecimal lossWeightJin;

    /** 损耗率（%） */
    private BigDecimal lossRate;

    /** 状态：completed/cancelled */
    private String status;

    /** 加工时间戳 */
    private Long processingDate;

    /** 作废时间戳 */
    private Long cancelTime;

    /** 备注 */
    private String remark;

    /** 操作人ID */
    private String operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 创建时间戳 */
    private Long createTime;

    /** 更新时间戳 */
    private Long updateTime;
}
