package com.saas.fruit.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应格式
 * 与前端约定的响应结构：{code, message, data}
 * code=0表示成功，非0表示错误
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 状态码：0-成功，400-参数错误，401-未认证，403-禁止，404-未找到，500-服务器错误 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "操作成功", data);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(0, message, data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }
}
