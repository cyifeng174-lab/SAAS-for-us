package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 规格类型实体
 */
@Data
@TableName("spec_categories")
public class SpecCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 类型编号 */
    private String categoryNo;

    /** 规格类型名称 */
    private String name;

    /** 单位 */
    private String unit;

    /** 排序值 */
    private Integer sortOrder;

    /** 状态：active/archived */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间戳 */
    private Long createTime;

    /** 更新时间戳 */
    private Long updateTime;
}
