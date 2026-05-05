package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 客户实体
 */
@Data
@TableName("customers")
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 客户编号 */
    private String customerNo;

    /** 客户名称 */
    private String name;

    /** 联系电话 */
    private String phone;

    /** 地址 */
    private String address;

    /** 客户类型：wholesale/retail/both */
    private String customerType;

    /** 信用额度（分） */
    private Integer creditLimitFen;

    /** 当前总欠款（分） */
    private Integer totalDebtFen;

    /** 累计销售额（分） */
    private Integer totalSalesFen;

    /** 累计已收款（分） */
    private Integer totalPaidFen;

    /** 订单数量 */
    private Integer orderCount;

    /** 首次下单时间戳 */
    private Long firstOrderTime;

    /** 最近下单时间戳 */
    private Long lastOrderTime;

    /** 状态：active/inactive/blacklist/deleted */
    private String status;

    /** 软删除时间戳 */
    private Long deleteTime;

    /** 备注 */
    private String remark;

    /** 创建时间戳 */
    private Long createTime;

    /** 更新时间戳 */
    private Long updateTime;
}
