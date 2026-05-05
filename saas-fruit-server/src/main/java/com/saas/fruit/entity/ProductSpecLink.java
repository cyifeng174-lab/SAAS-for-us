package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品规格关联实体
 */
@Data
@TableName("product_spec_links")
public class ProductSpecLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 成品名称 */
    private String productName;

    /** 规格类型ID */
    private Long categoryId;

    /** 规格类型名称（冗余） */
    private String categoryName;

    /** 是否必选 */
    private Boolean isRequired;

    /** 创建时间戳 */
    private Long createTime;

    /** 更新时间戳 */
    private Long updateTime;
}
