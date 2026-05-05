package com.saas.fruit.service;

import com.saas.fruit.dto.response.LoginResponse;
import com.saas.fruit.security.LoginUser;

/**
 * 认证服务接口
 * 负责微信小程序登录认证、Token签发与当前用户校验
 */
public interface AuthService {

    /**
     * 微信小程序登录
     * 接收前端 wx.login() 获取的临时code，完成认证后返回JWT Token及用户信息
     *
     * @param code 微信登录凭证code，通过 wx.login() 获取
     * @return 登录响应，包含JWT Token与用户基本信息
     */
    LoginResponse login(String code);

    /**
     * 校验当前登录用户
     * 从请求上下文（ThreadLocal）中获取已认证的用户信息，
     * 若未登录或Token已过期则抛出BusinessException
     *
     * @return 当前登录用户信息
     */
    LoginUser checkAuth();
}
