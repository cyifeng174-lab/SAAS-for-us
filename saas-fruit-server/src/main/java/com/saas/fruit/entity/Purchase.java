package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购单实体
 */
@Data
@TableName("purchases")
public class Purchase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 采购批次号 */
    private String batchNo;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称（冗余） */
    private String supplierName;

    /** 原果名称 */
    private String fruitName;

    /** 产地 */
    private String origin;

    /** 重量（斤） */
    private BigDecimal weightJin;

    /** 单价（分/斤） */
    private Integer unitPriceFen;

    /** 总金额（分） */
    private Integer totalAmountFen;

    /** 付款状态：unpaid/partial/paid */
    private String paymentStatus;

    /** 已付金额（分） */
    private Integer paidAmountFen;

    /** 欠款金额（分） */
    private Integer debtAmountFen;

    /** 采购日期时间戳 */
    private Long purchaseDate;

    /** 状态：pending/processing/completed/cancelled */
    private String status;

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
