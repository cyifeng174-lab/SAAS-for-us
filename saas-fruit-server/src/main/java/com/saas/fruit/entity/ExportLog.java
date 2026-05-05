package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 导出日志实体
 */
@Data
@TableName("export_logs")
public class ExportLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 导出模块 */
    private String module;

    /** 模块中文名 */
    private String moduleName;

    /** 导出格式：csv/json/excel */
    private String format;

    /** 筛选开始日期 */
    private String dateFrom;

    /** 筛选截止日期 */
    private String dateTo;

    /** 状态筛选 */
    private String statusFilter;

    /** 导出条数 */
    private Integer count;

    /** 符合条件的总数 */
    private Integer totalCount;

    /** 结果：success/failed */
    private String result;

    /** 操作时间戳 */
    private Long createTime;
}
