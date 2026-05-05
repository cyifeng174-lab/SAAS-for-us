package com.saas.fruit.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JWT认证过滤器
 * 拦截所有API请求，验证Token并将用户信息存入上下文
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Value("${app.mock-login:false}")
    private boolean mockLogin;

    @Value("${app.mock-openid:mock_openid_dev_001}")
    private String mockOpenid;

    @Value("${app.mock-nickname:测试用户}")
    private String mockNickname;

    /** 不需要认证的路径 */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/auth/login",
            "/v3/api-docs",
            "/swagger",
            "/doc.html",
            "/webjars"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // 白名单路径直接放行
        for (String whitePath : WHITE_LIST) {
            if (requestUri.startsWith(whitePath)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        try {
            // Mock登录模式：本地开发跳过真实认证
            if (mockLogin) {
                LoginUser mockUser = new LoginUser();
                mockUser.setUserId(1L);
                mockUser.setOpenid(mockOpenid);
                mockUser.setTenantId("default");
                mockUser.setRole("admin");
                mockUser.setNickname(mockNickname);
                mockUser.setMockLogin(true);
                LoginUserContext.set(mockUser);
                filterChain.doFilter(request, response);
                return;
            }

            // 真实认证：从Header中获取Token
            String authHeader = request.getHeader("Authorization");
            if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
                sendUnauthorized(response, "未提供有效的认证Token");
                return;
            }

            String token = authHeader.substring(7);
            LoginUser loginUser = jwtTokenUtil.parseLoginUser(token);
            LoginUserContext.set(loginUser);
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.warn("JWT认证失败：{}", e.getMessage());
            sendUnauthorized(response, e.getMessage());
        } finally {
            // 请求结束后清理ThreadLocal
            LoginUserContext.clear();
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }
}
