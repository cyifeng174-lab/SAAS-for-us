package com.saas.fruit.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额工具类
 * 系统内部金额统一以"分"为单位存储（int类型）
 * 前端展示和录入以"元"为单位
 */
public class AmountUtil {

    /** 分转元（保留2位小数） */
    public static String fenToYuanString(Integer fen) {
        if (fen == null || fen == 0) return "0.00";
        return new BigDecimal(fen).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP).toString();
    }

    /** 分转元（返回数值） */
    public static BigDecimal fenToYuan(Integer fen) {
        if (fen == null) return BigDecimal.ZERO;
        return new BigDecimal(fen).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    /** 元转分 */
    public static int yuanToFen(BigDecimal yuan) {
        if (yuan == null) return 0;
        return yuan.multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /** 元转分（字符串输入） */
    public static int yuanToFen(String yuanStr) {
        if (yuanStr == null || yuanStr.trim().isEmpty()) return 0;
        return yuanToFen(new BigDecimal(yuanStr.trim()));
    }

    /** 计算金额 = 重量 × 单价，向下取整 */
    public static int calcAmount(BigDecimal weight, int unitPriceFen) {
        if (weight == null) return 0;
        return weight.multiply(new BigDecimal(unitPriceFen)).setScale(0, RoundingMode.FLOOR).intValue();
    }
}
