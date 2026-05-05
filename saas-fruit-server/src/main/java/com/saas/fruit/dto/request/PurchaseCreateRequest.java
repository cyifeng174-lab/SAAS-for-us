package com.saas.fruit.dto.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购单创建请求DTO
 * 用于新增采购单时接收前端提交的数据
 * 
 * 金额单位说明：系统内部统一以"分"存储，此DTO中的金额字段已是"分"
 * 重量字段以"斤"为单位，使用BigDecimal保证精度
 */
@Data
public class PurchaseCreateRequest {

    /** 供应商ID（必填） */
    private Long supplierId;

    /** 供应商名称（必填，冗余存储，方便查询时无需关联） */
    private String supplierName;

    /** 原果名称（必填） */
    private String fruitName;

    /** 产地 */
    private String origin;

    /** 重量（斤），必须大于0 */
    private BigDecimal weightJin;

    /** 单价（分/斤），必须 >= 0 */
    private Integer unitPriceFen;

    /** 总金额（分），必须 >= 0 */
    private Integer totalAmountFen;

    /** 付款状态：unpaid-未付 / partial-部分付款 / paid-已付清，默认unpaid */
    private String paymentStatus;

    /** 已付金额（分），默认0 */
    private Integer paidAmountFen;

    /** 欠款金额（分），默认0 */
    private Integer debtAmountFen;

    /** 操作人ID */
    private String operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 备注 */
    private String remark;
}
