package com.saas.fruit.dto.response;

import lombok.Data;

/**
 * 导出响应DTO
 * 返回数据导出操作的结果信息
 * 包含导出文件的基础信息和统计元数据
 */
@Data
public class ExportResponse {

    /** 导出文件名 */
    private String fileName;

    /** 文件内容（Base64编码或文本内容） */
    private String fileContent;

    /** MIME类型，如：text/csv, application/json, application/vnd.ms-excel */
    private String mimeType;

    /** 导出格式：csv/json/excel */
    private String format;

    /** 符合条件的总记录数 */
    private Integer totalCount;

    /** 实际导出的记录数 */
    private Integer exportedCount;

    /** 导出模块标识，如：customers/sales/purchases/inventory */
    private String module;

    /** 模块中文名称 */
    private String moduleName;

    /** 导出时间戳（毫秒） */
    private Long timestamp;
}
