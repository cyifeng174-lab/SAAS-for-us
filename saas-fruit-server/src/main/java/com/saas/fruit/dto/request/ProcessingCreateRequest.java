package com.saas.fruit.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 加工单创建请求DTO
 * 用于新增加工订单时接收前端提交的数据
 * 将采购的原果加工为成品，记录成本投入和产出明细
 */
@Data
public class ProcessingCreateRequest {

    /** 来源采购单ID */
    private Long purchaseId;

    /** 加工成本明细 */
    private ProcessingCosts costs;

    /** 产出商品列表 */
    private List<OutputItem> outputs;

    /** 备注 */
    private String remark;

    /** 操作人ID */
    private String operatorId;

    /** 操作人名称 */
    private String operatorName;

    /**
     * 加工成本明细
     * 分项记录加工过程中的各项费用
     * 所有金额均以"分"为单位存储
     */
    @Data
    public static class ProcessingCosts {

        /** 人工费用（分） */
        private Integer laborCostFen;

        /** 包装费用（分） */
        private Integer packagingCostFen;

        /** 运输费用（分） */
        private Integer transportCostFen;

        /** 其他费用（分） */
        private Integer otherCostFen;
    }

    /**
     * 加工产出商品项
     * 描述加工后生成的成品信息
     */
    @Data
    public static class OutputItem {

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

        /** 产出重量（斤），使用BigDecimal保证精度 */
        private BigDecimal weightJin;
    }
}
