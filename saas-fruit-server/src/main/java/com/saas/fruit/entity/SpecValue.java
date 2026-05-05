package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 规格值实体
 */
@Data
@TableName("spec_values")
public class SpecValue {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 关联规格类型ID */
    private Long categoryId;

    /** 规格类型名称（冗余） */
    private String categoryName;

    /** 规格值 */
    private String value;

    /** 显示标签 */
    private String label;

    /** 排序值 */
    private Integer sortOrder;

    /** 状态：active/archived */
    private String status;

    /** 创建时间戳 */
    private Long createTime;

    /** 更新时间戳 */
    private Long updateTime;
}
