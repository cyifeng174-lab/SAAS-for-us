package com.saas.fruit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户实体
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 微信openid */
    private String openid;

    /** 租户ID */
    private String tenantId;

    /** 角色：admin/staff */
    private String role;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatarUrl;

    /** 手机号 */
    private String phone;

    /** 状态：active/inactive/banned */
    private String status;

    /** 最后登录时间戳(ms) */
    private Long lastLoginTime;

    /** 创建时间戳(ms) */
    private Long createTime;

    /** 更新时间戳(ms) */
    private Long updateTime;
}
