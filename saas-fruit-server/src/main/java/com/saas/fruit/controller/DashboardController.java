package com.saas.fruit.controller;

import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.dto.response.DashboardResponse;
import com.saas.fruit.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台仪表盘控制器
 * 汇总展示当天、当月的经营数据和关键指标
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * 获取仪表盘数据
     * 包含今日销售、今日收款、本月销售、客户总览、库存总览五大板块
     */
    @GetMapping
    public ApiResponse<DashboardResponse> get() {
        log.info("[仪表盘查询] 请求仪表盘数据");
        DashboardResponse response = dashboardService.get();
        return ApiResponse.success(response);
    }
}
