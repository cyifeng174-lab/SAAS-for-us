package com.saas.fruit.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 财务收款请求DTO
 * 用于记录客户的回款操作，支持同时核销多笔订单
 * 所有金额均以"分"为单位存储，避免浮点精度问题
 */
@Data
public class FinanceReceiveRequest {

    /** 客户ID */
    private Long customerId;

    /** 收款金额（分） */
    private Integer amountFen;

    /** 收款方式：wechat/alipay/cash/bank/transfer */
    private String paymentMethod;

    /** 收款时间戳（毫秒） */
    private Long receiveTime;

    /** 备注 */
    private String remark;

    /** 核销明细列表，指定本次收款分别核销哪些订单 */
    private List<WriteOffDetail> writeOffDetails;

    /** 操作人ID */
    private String operatorId;

    /** 操作人名称 */
    private String operatorName;

    /**
     * 核销明细项
     * 每条明细记录一笔订单的核销金额
     */
    @Data
    public static class WriteOffDetail {

        /** 被核销的销售订单ID */
        private Long orderId;

        /** 本次核销金额（分） */
        private Integer writeOffAmountFen;
    }
}
