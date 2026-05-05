package com.saas.fruit.security;

/**
 * 登录用户上下文持有者
 * 使用ThreadLocal存储当前请求的登录用户信息
 */
public class LoginUserContext {

    private static final ThreadLocal<LoginUser> USER_HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        USER_HOLDER.set(user);
    }

    public static LoginUser get() {
        return USER_HOLDER.get();
    }

    public static String getTenantId() {
        LoginUser user = get();
        return user != null ? user.getTenantId() : "default";
    }

    public static Long getUserId() {
        LoginUser user = get();
        return user != null ? user.getUserId() : null;
    }

    public static void clear() {
        USER_HOLDER.remove();
    }
}
