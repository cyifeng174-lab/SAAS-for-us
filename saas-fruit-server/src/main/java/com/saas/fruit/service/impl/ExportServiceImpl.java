package com.saas.fruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saas.fruit.dto.response.ExportResponse;
import com.saas.fruit.entity.Customer;
import com.saas.fruit.entity.Inventory;
import com.saas.fruit.entity.SalesOrder;
import com.saas.fruit.entity.Supplier;
import com.saas.fruit.mapper.CustomerMapper;
import com.saas.fruit.mapper.InventoryMapper;
import com.saas.fruit.mapper.SalesOrderMapper;
import com.saas.fruit.mapper.SupplierMapper;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.service.ExportService;
import com.saas.fruit.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;

/**
 * 数据导出服务实现类
 * 简版导出：根据模块名称查询对应数据表，转换为CSV格式返回
 * 支持按日期范围、状态、关键词筛选
 */
@Slf4j
@Service
public class ExportServiceImpl implements ExportService {

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private SupplierMapper supplierMapper;

    /** 日期格式化器 */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * 导出数据
     * 根据module切换不同的查询逻辑，转化为CSV格式
     */
    @Override
    public ExportResponse export(String module, String format, String dateFrom, String dateTo,
                                  String status, String keyword) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        // 默认格式为CSV
        if (!StringUtils.hasText(format)) {
            format = "csv";
        }

        String moduleName;
        String csvContent;
        int totalCount;

        // 根据模块分发到不同的导出逻辑
        switch (module) {
            case "customers":
                moduleName = "客户列表";
                CsvResult customerResult = exportCustomers(tenantId, keyword, status);
                csvContent = customerResult.content;
                totalCount = customerResult.count;
                break;
            case "sales":
                moduleName = "销售订单";
                CsvResult salesResult = exportSales(tenantId, keyword, status, dateFrom, dateTo);
                csvContent = salesResult.content;
                totalCount = salesResult.count;
                break;
            case "inventory":
                moduleName = "库存列表";
                CsvResult inventoryResult = exportInventory(tenantId, keyword, status);
                csvContent = inventoryResult.content;
                totalCount = inventoryResult.count;
                break;
            case "suppliers":
                moduleName = "供应商列表";
                CsvResult supplierResult = exportSuppliers(tenantId, keyword, status);
                csvContent = supplierResult.content;
                totalCount = supplierResult.count;
                break;
            default:
                throw new com.saas.fruit.common.BusinessException(400, "不支持的导出模块: " + module);
        }

        // 生成文件名
        String fileName = moduleName + "_" + DATE_FORMAT.format(new Date())
                + "." + (format.equals("csv") ? "csv" : "json");

        // 构建导出响应
        ExportResponse response = new ExportResponse();
        response.setFileName(fileName);
        response.setFileContent(csvContent);
        response.setMimeType("text/csv; charset=UTF-8");
        response.setFormat(format);
        response.setTotalCount(totalCount);
        response.setExportedCount(totalCount);
        response.setModule(module);
        response.setModuleName(moduleName);
        response.setTimestamp(now);

        log.info("[数据导出] 租户={}, 模块={}, 格式={}, 导出条数={}", tenantId, module, format, totalCount);

        return response;
    }

    // ==================== 各模块导出方法 ====================

    /**
     * 导出客户列表
     */
    private CsvResult exportCustomers(String tenantId, String keyword, String status) {
        LambdaQueryWrapper<Customer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Customer::getTenantId, tenantId);
        queryWrapper.ne(Customer::getStatus, "deleted");

        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(w -> w.like(Customer::getName, keyword)
                    .or().like(Customer::getPhone, keyword));
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Customer::getStatus, status);
        }

        List<Customer> list = customerMapper.selectList(queryWrapper);

        // CSV表头
        StringJoiner header = new StringJoiner(",");
        header.add("客户编号");
        header.add("客户名称");
        header.add("联系电话");
        header.add("客户类型");
        header.add("信用额度");
        header.add("当前欠款");
        header.add("累计销售额");
        header.add("累计已收款");
        header.add("状态");
        header.add("创建时间");

        StringBuilder sb = new StringBuilder();
        // 添加BOM头，确保Excel正确识别UTF-8编码
        sb.append("\uFEFF");
        sb.append(header.toString()).append("\n");

        for (Customer c : list) {
            StringJoiner row = new StringJoiner(",");
            row.add(escapeCsv(c.getCustomerNo()));
            row.add(escapeCsv(c.getName()));
            row.add(escapeCsv(c.getPhone()));
            row.add(escapeCsv(c.getCustomerType()));
            row.add(String.valueOf(c.getCreditLimitFen() != null ? c.getCreditLimitFen() : 0));
            row.add(String.valueOf(c.getTotalDebtFen() != null ? c.getTotalDebtFen() : 0));
            row.add(String.valueOf(c.getTotalSalesFen() != null ? c.getTotalSalesFen() : 0));
            row.add(String.valueOf(c.getTotalPaidFen() != null ? c.getTotalPaidFen() : 0));
            row.add(escapeCsv(c.getStatus()));
            row.add(escapeCsv(DateUtil.formatTime(c.getCreateTime())));
            sb.append(row.toString()).append("\n");
        }

        return new CsvResult(sb.toString(), list.size());
    }

    /**
     * 导出销售订单
     */
    private CsvResult exportSales(String tenantId, String keyword, String status,
                                   String dateFrom, String dateTo) {
        LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SalesOrder::getTenantId, tenantId);

        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(w -> w.like(SalesOrder::getOrderNo, keyword)
                    .or().like(SalesOrder::getCustomerName, keyword));
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(SalesOrder::getStatus, status);
        }
        // 日期范围筛选
        if (StringUtils.hasText(dateFrom)) {
            queryWrapper.ge(SalesOrder::getCreateTime, parseDateToTimestamp(dateFrom));
        }
        if (StringUtils.hasText(dateTo)) {
            queryWrapper.le(SalesOrder::getCreateTime, parseDateToTimestamp(dateTo) + 86400000L - 1);
        }
        queryWrapper.orderByDesc(SalesOrder::getCreateTime);

        List<SalesOrder> list = salesOrderMapper.selectList(queryWrapper);

        StringJoiner header = new StringJoiner(",");
        header.add("销售单号");
        header.add("客户名称");
        header.add("客户电话");
        header.add("销售类型");
        header.add("总重量(斤)");
        header.add("总金额(分)");
        header.add("毛利(分)");
        header.add("收款状态");
        header.add("实收金额(分)");
        header.add("欠款金额(分)");
        header.add("下单时间");
        header.add("状态");

        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF");
        sb.append(header.toString()).append("\n");

        for (SalesOrder o : list) {
            StringJoiner row = new StringJoiner(",");
            row.add(escapeCsv(o.getOrderNo()));
            row.add(escapeCsv(o.getCustomerName()));
            row.add(escapeCsv(o.getCustomerPhone()));
            row.add(escapeCsv(o.getSaleType()));
            row.add(o.getTotalWeightJin() != null ? o.getTotalWeightJin().toPlainString() : "0");
            row.add(String.valueOf(o.getTotalAmountFen() != null ? o.getTotalAmountFen() : 0));
            row.add(String.valueOf(o.getTotalProfitFen() != null ? o.getTotalProfitFen() : 0));
            row.add(escapeCsv(o.getPaymentStatus()));
            row.add(String.valueOf(o.getPaidAmountFen() != null ? o.getPaidAmountFen() : 0));
            row.add(String.valueOf(o.getDebtAmountFen() != null ? o.getDebtAmountFen() : 0));
            row.add(escapeCsv(DateUtil.formatTime(o.getCreateTime())));
            row.add(escapeCsv(o.getStatus()));
            sb.append(row.toString()).append("\n");
        }

        return new CsvResult(sb.toString(), list.size());
    }

    /**
     * 导出库存列表
     */
    private CsvResult exportInventory(String tenantId, String keyword, String status) {
        LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Inventory::getTenantId, tenantId);

        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(w -> w.like(Inventory::getProductName, keyword)
                    .or().like(Inventory::getFruitName, keyword)
                    .or().like(Inventory::getGrade, keyword)
                    .or().like(Inventory::getSpec, keyword));
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Inventory::getStatus, status);
        }

        List<Inventory> list = inventoryMapper.selectList(queryWrapper);

        StringJoiner header = new StringJoiner(",");
        header.add("成品名称");
        header.add("水果名称");
        header.add("等级");
        header.add("规格");
        header.add("产地");
        header.add("当前库存(斤)");
        header.add("单位成本(分/斤)");
        header.add("总成本(分)");
        header.add("建议售价(分/斤)");
        header.add("累计入库(斤)");
        header.add("累计出库(斤)");
        header.add("状态");
        header.add("最近出库时间");

        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF");
        sb.append(header.toString()).append("\n");

        for (Inventory i : list) {
            StringJoiner row = new StringJoiner(",");
            row.add(escapeCsv(i.getProductName()));
            row.add(escapeCsv(i.getFruitName()));
            row.add(escapeCsv(i.getGrade()));
            row.add(escapeCsv(i.getSpec()));
            row.add(escapeCsv(i.getOrigin()));
            row.add(i.getCurrentStockJin() != null ? i.getCurrentStockJin().toPlainString() : "0");
            row.add(String.valueOf(i.getUnitCostFen() != null ? i.getUnitCostFen() : 0));
            row.add(String.valueOf(i.getTotalCostFen() != null ? i.getTotalCostFen() : 0));
            row.add(String.valueOf(i.getSuggestedPriceFen() != null ? i.getSuggestedPriceFen() : 0));
            row.add(i.getTotalInboundJin() != null ? i.getTotalInboundJin().toPlainString() : "0");
            row.add(i.getTotalOutboundJin() != null ? i.getTotalOutboundJin().toPlainString() : "0");
            row.add(escapeCsv(i.getStatus()));
            row.add(escapeCsv(DateUtil.formatTime(i.getLastOutboundTime())));
            sb.append(row.toString()).append("\n");
        }

        return new CsvResult(sb.toString(), list.size());
    }

    /**
     * 导出供应商列表
     */
    private CsvResult exportSuppliers(String tenantId, String keyword, String status) {
        LambdaQueryWrapper<Supplier> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Supplier::getTenantId, tenantId);
        queryWrapper.ne(Supplier::getStatus, "deleted");

        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(w -> w.like(Supplier::getName, keyword)
                    .or().like(Supplier::getPhone, keyword)
                    .or().like(Supplier::getContactPerson, keyword)
                    .or().like(Supplier::getSupplierNo, keyword));
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Supplier::getStatus, status);
        }

        List<Supplier> list = supplierMapper.selectList(queryWrapper);

        StringJoiner header = new StringJoiner(",");
        header.add("供应商编号");
        header.add("供应商名称");
        header.add("联系电话");
        header.add("联系人");
        header.add("地址");
        header.add("产地");
        header.add("供应商类型");
        header.add("当前欠款(分)");
        header.add("累计采购额(分)");
        header.add("状态");
        header.add("创建时间");

        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF");
        sb.append(header.toString()).append("\n");

        for (Supplier s : list) {
            StringJoiner row = new StringJoiner(",");
            row.add(escapeCsv(s.getSupplierNo()));
            row.add(escapeCsv(s.getName()));
            row.add(escapeCsv(s.getPhone()));
            row.add(escapeCsv(s.getContactPerson()));
            row.add(escapeCsv(s.getAddress()));
            row.add(escapeCsv(s.getOrigin()));
            row.add(escapeCsv(s.getSupplierType()));
            row.add(String.valueOf(s.getTotalDebtFen() != null ? s.getTotalDebtFen() : 0));
            row.add(String.valueOf(s.getTotalPurchaseFen() != null ? s.getTotalPurchaseFen() : 0));
            row.add(escapeCsv(s.getStatus()));
            row.add(escapeCsv(DateUtil.formatTime(s.getCreateTime())));
            sb.append(row.toString()).append("\n");
        }

        return new CsvResult(sb.toString(), list.size());
    }

    // ==================== 辅助方法 ====================

    /**
     * CSV字段转义：如果包含逗号、双引号或换行符，用双引号包裹
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 将日期字符串 (yyyy-MM-dd) 转为时间戳（毫秒，取当天 00:00:00）
     */
    private long parseDateToTimestamp(String dateStr) {
        try {
            Date date = DATE_FORMAT.parse(dateStr);
            return date.getTime();
        } catch (Exception e) {
            log.warn("日期解析失败: {}", dateStr);
            return 0L;
        }
    }

    // ==================== 内部类 ====================

    /**
     * CSV导出结果封装
     */
    private static class CsvResult {
        /** CSV内容 */
        final String content;
        /** 记录条数 */
        final int count;

        CsvResult(String content, int count) {
            this.content = content;
            this.count = count;
        }
    }
}
