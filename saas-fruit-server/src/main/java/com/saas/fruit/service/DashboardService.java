package com.saas.fruit.service;

import com.saas.fruit.dto.response.DashboardResponse;

/**
 * 工作台仪表盘服务接口
 * 汇总展示当天、当月的经营数据和关键指标
 * 翻译自 data_dashboard 云函数的查询逻辑
 */
public interface DashboardService {

    /**
     * 获取仪表盘数据
     * 包含今日销售、今日收款、本月销售、客户总览、库存总览五大板块
     *
     * @return 仪表盘响应DTO，包含各项经营指标
     */
    DashboardResponse get();
}
