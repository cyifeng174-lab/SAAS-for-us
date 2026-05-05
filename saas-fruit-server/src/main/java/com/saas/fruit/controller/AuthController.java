package com.saas.fruit.controller;

import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.dto.request.LoginRequest;
import com.saas.fruit.dto.response.LoginResponse;
import com.saas.fruit.security.LoginUser;
import com.saas.fruit.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 负责微信小程序登录认证、Token校验
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 微信小程序登录
     * 接收前端 wx.login() 获取的临时code，完成认证后返回JWT Token及用户信息
     *
     * @param req 登录请求（含微信登录code）
     * @return 登录响应，包含JWT Token与用户基本信息
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req) {
        log.info("[登录请求] code={}", req.getCode());
        LoginResponse response = authService.login(req.getCode());
        return ApiResponse.success(response, "登录成功");
    }

    /**
     * 校验当前登录用户
     * 从JWT Token中解析用户信息并返回
     *
     * @return 当前登录用户信息
     */
    @GetMapping("/check")
    public ApiResponse<LoginUser> check() {
        LoginUser loginUser = authService.checkAuth();
        return ApiResponse.success(loginUser);
    }
}
