package com.saas.fruit.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 工作台仪表盘响应DTO
 * 汇总展示当天、当月的经营数据和关键指标
 * 包含销售概况、客户总览、库存总览三大板块
 */
@Data
public class DashboardResponse {

    /** 今日数据概览 */
    private DashboardToday today;

    /** 本月数据概览 */
    private DashboardMonth month;

    /** 客户总览数据 */
    private DashboardCustomers customers;

    /** 库存总览数据 */
    private DashboardInventory inventory;

    // ==================== 内部类：今日概览 ====================

    /**
     * 今日经营数据概览
     */
    @Data
    public static class DashboardToday {

        /** 今日销售额（元），以"分"存储由前端换算为元展示 */
        private Integer salesYuan;

        /** 今日实收金额（元） */
        private Integer receivedYuan;

        /** 今日支出金额（元，采购付款等） */
        private Integer receiptsYuan;

        /** 今日订单数量 */
        private Integer orderCount;
    }

    // ==================== 内部类：本月概览 ====================

    /**
     * 本月经营数据概览
     */
    @Data
    public static class DashboardMonth {

        /** 本月销售额（元） */
        private Integer salesYuan;
    }

    // ==================== 内部类：客户总览 ====================

    /**
     * 客户总览数据
     */
    @Data
    public static class DashboardCustomers {

        /** 客户总数 */
        private Integer total;

        /** 客户总欠款金额（元） */
        private Integer totalDebtYuan;

        /** 欠款金额最高的前N名客户列表 */
        private List<DebtTopItem> topDebt;
    }

    /**
     * 欠款排行客户项
     */
    @Data
    public static class DebtTopItem {

        /** 客户名称 */
        private String name;

        /** 客户电话 */
        private String phone;

        /** 欠款金额（元） */
        private Integer debtYuan;
    }

    // ==================== 内部类：库存总览 ====================

    /**
     * 库存总览数据
     */
    @Data
    public static class DashboardInventory {

        /** 库存总价值（元），所有库存商品的成本合计 */
        private Integer totalValueYuan;

        /** 库存商品总数量（斤） */
        private Integer totalCount;

        /** 库存商品品种数 */
        private Integer productCount;
    }
}
