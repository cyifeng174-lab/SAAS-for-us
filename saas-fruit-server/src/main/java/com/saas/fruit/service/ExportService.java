package com.saas.fruit.service;

import com.saas.fruit.dto.response.ExportResponse;

/**
 * 数据导出服务接口
 * 根据指定模块查询对应表数据，转换为指定格式导出
 */
public interface ExportService {

    /**
     * 导出数据
     * 根据模块名称查询对应数据表，转换为指定格式（CSV/JSON/Excel）返回
     *
     * @param module   导出模块标识：customers/sales/purchases/inventory/suppliers
     * @param format   导出格式：csv/json/excel
     * @param dateFrom 筛选起始日期（可选，格式 yyyy-MM-dd）
     * @param dateTo   筛选截止日期（可选，格式 yyyy-MM-dd）
     * @param status   状态筛选（可选）
     * @param keyword  关键词搜索（可选）
     * @return 导出响应，包含文件内容和元数据
     */
    ExportResponse export(String module, String format, String dateFrom, String dateTo,
                           String status, String keyword);
}
