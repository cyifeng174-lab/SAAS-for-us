package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 财务流水实体
 */
@Data
@TableName("financial_ledgers")
public class FinancialLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 流水号 */
    private String ledgerNo;

    /** 流水类型：receivable-应收/payable-应付 */
    private String ledgerType;

    /** 交易类型：sale_debt/purchase_debt/payment_received/payment_made */
    private String transactionType;

    /** 金额（分） */
    private Integer amountFen;

    /** 交易前余额（分） */
    private Integer balanceBeforeFen;

    /** 交易后余额（分） */
    private Integer balanceAfterFen;

    /** 关联方类型：customer/supplier */
    private String relatedPartyType;

    /** 关联方ID */
    private Long relatedPartyId;

    /** 关联方名称（冗余） */
    private String relatedPartyName;

    /** 关联订单类型：sales_order/purchase_order/none */
    private String relatedOrderType;

    /** 关联订单ID */
    private Long relatedOrderId;

    /** 关联订单号 */
    private String relatedOrderNo;

    /** 核销明细（JSON） */
    private String writeOffDetails;

    /** 收款方式：wechat/alipay/cash/bank */
    private String paymentMethod;

    /** 交易时间戳 */
    private Long transactionTime;

    /** 备注 */
    private String remark;

    /** 操作人ID */
    private String operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 创建时间戳 */
    private Long createTime;
}
