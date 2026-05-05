package com.saas.fruit.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售开单请求DTO
 * 用于创建销售订单时接收前端提交的数据
 * 
 * 金额单位说明：系统内部统一以"分"存储，此DTO中的金额字段已是"分"
 * 重量字段以"斤"为单位，使用BigDecimal保证精度
 */
@Data
public class SalesOrderCreateRequest {

    /** 客户ID（必填） */
    private Long customerId;

    /** 销售类型：wholesale-批发 / retail-零售（必填） */
    private String saleType;

    /** 订单明细列表（必填，至少1项） */
    private List<OrderItemRequest> orderItems;

    /** 实收金额（分），默认0 */
    private Integer paidAmountFen;

    /** 操作人ID */
    private String operatorId;

    /** 操作人名称 */
    private String operatorName;

    /**
     * 销售订单明细项
     * 每一项对应一个库存商品的出库
     */
    @Data
    public static class OrderItemRequest {

        /** 库存ID（必填，关联inventories表主键） */
        private Long inventoryId;

        /** 重量（斤），必须大于0 */
        private BigDecimal weightJin;

        /** 单价（分/斤），必须 >= 0 */
        private Integer unitPriceFen;
    }
}
