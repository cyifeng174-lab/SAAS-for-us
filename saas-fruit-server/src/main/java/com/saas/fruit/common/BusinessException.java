package com.saas.fruit.common;

/**
 * 业务异常类
 * 用于在Service层抛出可预期的业务错误，由全局异常处理器统一捕获
 */
public class BusinessException extends RuntimeException {

    /** HTTP状态码 */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public int getCode() {
        return code;
    }
}
