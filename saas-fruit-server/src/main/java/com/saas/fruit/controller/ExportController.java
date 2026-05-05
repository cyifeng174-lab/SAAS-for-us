package com.saas.fruit.controller;

import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.dto.response.ExportResponse;
import com.saas.fruit.service.ExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 数据导出控制器
 * 负责各模块数据的导出（CSV/JSON/Excel格式）
 */
@Slf4j
@RestController
@RequestMapping("/api/export")
public class ExportController {

    @Autowired
    private ExportService exportService;

    /**
     * 导出数据
     * 根据模块名称查询对应数据表，转换为指定格式返回
     *
     * @param module   导出模块：customers/sales/inventory/suppliers
     * @param format   导出格式：csv/json/excel（默认csv）
     * @param dateFrom 筛选起始日期（可选）
     * @param dateTo   筛选截止日期（可选）
     * @param status   状态筛选（可选）
     * @param keyword  关键词搜索（可选）
     * @return 导出响应，包含文件内容和元数据
     */
    @PostMapping
    public ApiResponse<ExportResponse> export(
            @RequestParam String module,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {

        log.info("[数据导出] 模块={}, 格式={}, 日期范围=[{},{}}, 状态={}, 关键词={}",
                module, format, dateFrom, dateTo, status, keyword);

        ExportResponse response = exportService.export(module, format, dateFrom, dateTo, status, keyword);
        return ApiResponse.success(response, "导出成功");
    }
}
