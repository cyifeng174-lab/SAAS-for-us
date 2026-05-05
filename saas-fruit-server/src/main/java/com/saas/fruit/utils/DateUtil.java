package com.saas.fruit.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 时间工具类
 */
public class DateUtil {

    /** 获取当前时间戳（毫秒） */
    public static long now() {
        return System.currentTimeMillis();
    }

    /** 格式化时间戳为 yyyy-MM-dd HH:mm */
    public static String formatTime(Long timestamp) {
        if (timestamp == null || timestamp == 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(new Date(timestamp));
    }

    /** 获取今日起止时间戳 */
    public static long[] getTodayRange() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
        cal.set(java.util.Calendar.MINUTE, 59);
        cal.set(java.util.Calendar.SECOND, 59);
        cal.set(java.util.Calendar.MILLISECOND, 999);
        long end = cal.getTimeInMillis();
        return new long[]{start, end};
    }

    /** 获取本月起始时间戳 */
    public static long getMonthStart() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** 获取今日日期字符串 yyyyMMdd */
    public static String getDateStr() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        return sdf.format(new Date());
    }
}
