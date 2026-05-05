package com.saas.fruit.dto.request;

import lombok.Data;

/**
 * 登录请求DTO
 * 接收微信小程序端 wx.login 获取的临时code
 */
@Data
public class LoginRequest {

    /** 微信登录凭证code，通过 wx.login() 获取 */
    private String code;
}
