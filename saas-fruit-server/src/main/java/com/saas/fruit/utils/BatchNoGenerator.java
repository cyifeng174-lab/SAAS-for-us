package com.saas.fruit.utils;

import org.springframework.stereotype.Component;

/**
 * 单号生成器
 * 用于生成采购批次号、销售单号、加工单号、流水号、客户/供应商编号等
 */
@Component
public class BatchNoGenerator {

    /**
     * 生成采购批次号：CG + yyyyMMdd + 4位序号
     */
    public String genPurchaseBatchNo(long seq) {
        String dateStr = DateUtil.getDateStr();
        return "CG" + dateStr + padLeft(seq, 4);
    }

    /**
     * 生成销售单号：XS + yyyyMMdd + 4位序号
     */
    public String genSalesOrderNo(long seq) {
        String dateStr = DateUtil.getDateStr();
        return "XS" + dateStr + padLeft(seq, 4);
    }

    /**
     * 生成加工单号：JG + yyyyMMdd + 4位序号
     */
    public String genProcessingOrderNo(long seq) {
        String dateStr = DateUtil.getDateStr();
        return "JG" + dateStr + padLeft(seq, 4);
    }

    /**
     * 生成收款流水号：SK + yyyyMMdd + 4位序号
     */
    public String genReceiveLedgerNo(long seq) {
        String dateStr = DateUtil.getDateStr();
        return "SK" + dateStr + padLeft(seq, 4);
    }

    /**
     * 生成付款流水号：FK + yyyyMMdd + 4位序号
     */
    public String genPayLedgerNo(long seq) {
        String dateStr = DateUtil.getDateStr();
        return "FK" + dateStr + padLeft(seq, 4);
    }

    /**
     * 生成客户编号：KH + yyyyMMdd + 4位序号
     */
    public String genCustomerNo(long seq) {
        String dateStr = DateUtil.getDateStr();
        return "KH" + dateStr + padLeft(seq, 4);
    }

    /**
     * 生成供应商编号：GYS + yyyy + 4位序号
     */
    public String genSupplierNo(long seq) {
        String year = String.valueOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR));
        return "GYS" + year + padLeft(seq, 4);
    }

    /**
     * 生成批次ID：BATCH + 时间戳 + 4位随机数
     */
    public String genBatchId() {
        long timestamp = System.currentTimeMillis();
        int random = (int) (Math.random() * 10000);
        return "BATCH" + timestamp + padLeft(random, 4);
    }

    /**
     * 生成租户ID：T_ + 时间戳 + 4位随机字符
     */
    public String genTenantId() {
        long timestamp = System.currentTimeMillis();
        String random = Integer.toHexString((int) (Math.random() * 65535)).toUpperCase();
        return "T_" + timestamp + "_" + random;
    }

    private String padLeft(long num, int len) {
        StringBuilder sb = new StringBuilder();
        sb.append(num);
        while (sb.length() < len) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }
}
