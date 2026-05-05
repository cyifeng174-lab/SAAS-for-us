package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售订单实体
 */
@Data
@TableName("sales_orders")
public class SalesOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 销售单号 */
    private String orderNo;

    /** 客户ID */
    private Long customerId;

    /** 客户名称（冗余） */
    private String customerName;

    /** 客户电话（冗余） */
    private String customerPhone;

    /** 销售类型：wholesale/retail */
    private String saleType;

    /** 总重量（斤） */
    private BigDecimal totalWeightJin;

    /** 总金额（分） */
    private Integer totalAmountFen;

    /** 总成本（分，FIFO计算） */
    private Integer totalCostFen;

    /** 毛利（分） */
    private Integer totalProfitFen;

    /** 实收金额（分） */
    private Integer paidAmountFen;

    /** 欠款金额（分） */
    private Integer debtAmountFen;

    /** 收款状态：unpaid/partial/paid */
    private String paymentStatus;

    /** 订单状态：completed/cancelled */
    private String status;

    /** 下单时间戳 */
    private Long saleDate;

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
