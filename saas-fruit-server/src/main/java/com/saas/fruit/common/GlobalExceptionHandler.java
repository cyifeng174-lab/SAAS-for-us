package com.saas.fruit.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一处理所有Controller层抛出的异常，返回标准的ApiResponse格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 处理业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /** 处理参数校验异常 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数校验异常：{}", e.getMessage());
        return ApiResponse.error(400, e.getMessage());
    }

    /** 处理未知异常 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常：", e);
        return ApiResponse.error(500, "服务器内部错误：" + e.getMessage());
    }
}
