package com.saas.fruit.security;

import lombok.Data;

/**
 * 登录用户上下文信息
 * 由JWT Token解析后存入请求上下文，供后续业务使用
 */
@Data
public class LoginUser {

    /** 用户ID */
    private Long userId;

    /** 微信openid */
    private String openid;

    /** 租户ID */
    private String tenantId;

    /** 用户角色 */
    private String role;

    /** 用户昵称 */
    private String nickname;

    /** 是否为Mock登录 */
    private boolean mockLogin;
}
