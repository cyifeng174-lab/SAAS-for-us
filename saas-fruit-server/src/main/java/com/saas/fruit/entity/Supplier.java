package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 供应商实体
 */
@Data
@TableName("suppliers")
public class Supplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 供应商编号 */
    private String supplierNo;

    /** 供应商名称 */
    private String name;

    /** 联系电话 */
    private String phone;

    /** 联系人 */
    private String contactPerson;

    /** 地址 */
    private String address;

    /** 主要产地 */
    private String origin;

    /** 供应商类型：farmer/wholesaler/cooperative */
    private String supplierType;

    /** 当前总欠款（分，正=我欠供应商） */
    private Integer totalDebtFen;

    /** 累计采购额（分） */
    private Integer totalPurchaseFen;

    /** 累计已付款（分） */
    private Integer totalPaidFen;

    /** 采购次数 */
    private Integer purchaseCount;

    /** 首次采购时间戳 */
    private Long firstPurchaseTime;

    /** 最近采购时间戳 */
    private Long lastPurchaseTime;

    /** 状态：active/inactive/blacklist/deleted */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间戳 */
    private Long createTime;

    /** 更新时间戳 */
    private Long updateTime;
}
