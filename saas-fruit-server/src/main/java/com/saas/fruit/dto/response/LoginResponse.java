package com.saas.fruit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 * 包含JWT Token和基本用户信息，前端存储Token后用于后续API认证
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT认证Token，前端需存储在本地并在后续请求Header中携带 */
    private String token;

    /** 用户ID */
    private Long userId;

    /** 微信openid */
    private String openid;

    /** 租户ID */
    private String tenantId;

    /** 用户角色：admin/staff */
    private String role;

    /** 用户昵称 */
    private String nickname;

    /** 是否为新注册用户 */
    private boolean newUser;
}
