package com.saas.fruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saas.fruit.dto.response.DashboardResponse;
import com.saas.fruit.entity.Customer;
import com.saas.fruit.entity.Inventory;
import com.saas.fruit.entity.SalesOrder;
import com.saas.fruit.mapper.CustomerMapper;
import com.saas.fruit.mapper.InventoryMapper;
import com.saas.fruit.mapper.SalesOrderMapper;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.service.DashboardService;
import com.saas.fruit.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作台仪表盘服务实现类
 * 翻译自 data_dashboard 云函数的查询逻辑
 * 汇总展示当日、当月的经营数据和关键指标
 */
@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    /**
     * 获取仪表盘数据
     * 包含三大板块：今日/本月销售概况、客户总览、库存总览
     */
    @Override
    public DashboardResponse get() {
        String tenantId = LoginUserContext.getTenantId();

        DashboardResponse response = new DashboardResponse();

        // ========== 获取时间范围 ==========
        long[] todayRange = DateUtil.getTodayRange();
        long todayStart = todayRange[0];
        long todayEnd = todayRange[1];
        // 本月起始（当月1日 00:00:00）
        long monthStart = DateUtil.getMonthStart();

        // ========== 1. 今日数据概览 ==========
        DashboardResponse.DashboardToday today = buildTodayData(tenantId, todayStart, todayEnd);
        response.setToday(today);

        // ========== 2. 本月数据概览 ==========
        DashboardResponse.DashboardMonth month = buildMonthData(tenantId, monthStart);
        response.setMonth(month);

        // ========== 3. 客户总览 ==========
        DashboardResponse.DashboardCustomers customers = buildCustomersData(tenantId);
        response.setCustomers(customers);

        // ========== 4. 库存总览 ==========
        DashboardResponse.DashboardInventory inventory = buildInventoryData(tenantId);
        response.setInventory(inventory);

        log.info("[仪表盘查询] 租户={}, 今日销售额={}, 本月销售额={}, 客户数={}, 库存品种数={}",
                tenantId, today.getSalesYuan(), month.getSalesYuan(),
                customers.getTotal(), inventory.getProductCount());

        return response;
    }

    /**
     * 构建今日数据概览
     * - 今日销售额：查询 sales_orders 中 create_time 在今日范围内的，SUM totalAmountFen
     * - 今日订单数：COUNT 今日内的销售订单
     */
    private DashboardResponse.DashboardToday buildTodayData(String tenantId, long start, long end) {
        // 查询今日的销售订单（状态为completed，排除已取消的）
        LambdaQueryWrapper<SalesOrder> salesWrapper = new LambdaQueryWrapper<>();
        salesWrapper.eq(SalesOrder::getTenantId, tenantId);
        salesWrapper.eq(SalesOrder::getStatus, "completed");
        salesWrapper.between(SalesOrder::getCreateTime, start, end);

        List<SalesOrder> todayOrders = salesOrderMapper.selectList(salesWrapper);

        // 汇总今日销售额和订单数
        int todaySalesFen = todayOrders.stream()
                .mapToInt(o -> o.getTotalAmountFen() != null ? o.getTotalAmountFen() : 0)
                .sum();
        int todayOrderCount = todayOrders.size();

        DashboardResponse.DashboardToday today = new DashboardResponse.DashboardToday();
        // 销售额：从"分"换算为"元"（整除，前端展示时补两位小数）
        today.setSalesYuan(fenToYuan(todaySalesFen));
        // 今日订单数
        today.setOrderCount(todayOrderCount);
        // 今日实收金额（从 sales_orders 的 paid_amount_fen 汇总）
        int todayPaidFen = todayOrders.stream()
                .mapToInt(o -> o.getPaidAmountFen() != null ? o.getPaidAmountFen() : 0)
                .sum();
        today.setReceivedYuan(fenToYuan(todayPaidFen));
        // 今日支出：暂不计算（可扩展）
        today.setReceiptsYuan(0);

        return today;
    }

    /**
     * 构建本月数据概览
     * - 本月销售额：查询 sales_orders 中 create_time 在本月范围内的，SUM totalAmountFen
     */
    private DashboardResponse.DashboardMonth buildMonthData(String tenantId, long monthStart) {
        LambdaQueryWrapper<SalesOrder> salesWrapper = new LambdaQueryWrapper<>();
        salesWrapper.eq(SalesOrder::getTenantId, tenantId);
        salesWrapper.eq(SalesOrder::getStatus, "completed");
        salesWrapper.ge(SalesOrder::getCreateTime, monthStart);

        List<SalesOrder> monthOrders = salesOrderMapper.selectList(salesWrapper);

        int monthSalesFen = monthOrders.stream()
                .mapToInt(o -> o.getTotalAmountFen() != null ? o.getTotalAmountFen() : 0)
                .sum();

        DashboardResponse.DashboardMonth month = new DashboardResponse.DashboardMonth();
        month.setSalesYuan(fenToYuan(monthSalesFen));

        return month;
    }

    /**
     * 构建客户总览数据
     * - 活跃客户统计：COUNT active 客户
     * - 客户总欠款：SUM totalDebtFen
     * - 欠款TOP10：ORDER BY totalDebtFen DESC LIMIT 10
     */
    private DashboardResponse.DashboardCustomers buildCustomersData(String tenantId) {
        // 查询所有活跃客户
        LambdaQueryWrapper<Customer> customerWrapper = new LambdaQueryWrapper<>();
        customerWrapper.eq(Customer::getTenantId, tenantId);
        customerWrapper.ne(Customer::getStatus, "deleted");

        List<Customer> allCustomers = customerMapper.selectList(customerWrapper);

        // 活跃客户数
        int totalCount = allCustomers.size();

        // 汇总总欠款
        int totalDebtFen = allCustomers.stream()
                .mapToInt(c -> c.getTotalDebtFen() != null ? c.getTotalDebtFen() : 0)
                .sum();

        // 欠款TOP10：按 totalDebtFen 降序，取前10
        List<DashboardResponse.DebtTopItem> topDebtList = allCustomers.stream()
                .filter(c -> c.getTotalDebtFen() != null && c.getTotalDebtFen() > 0)
                .sorted((c1, c2) -> Integer.compare(
                        c2.getTotalDebtFen() != null ? c2.getTotalDebtFen() : 0,
                        c1.getTotalDebtFen() != null ? c1.getTotalDebtFen() : 0))
                .limit(10)
                .map(c -> {
                    DashboardResponse.DebtTopItem item = new DashboardResponse.DebtTopItem();
                    item.setName(c.getName());
                    item.setPhone(c.getPhone());
                    item.setDebtYuan(fenToYuan(c.getTotalDebtFen()));
                    return item;
                })
                .collect(Collectors.toList());

        DashboardResponse.DashboardCustomers customers = new DashboardResponse.DashboardCustomers();
        customers.setTotal(totalCount);
        customers.setTotalDebtYuan(fenToYuan(totalDebtFen));
        customers.setTopDebt(topDebtList);

        return customers;
    }

    /**
     * 构建库存总览数据
     * - 库存总价值：SUM(quantity * unitCostFen)，即所有库存记录的 totalCostFen 合计
     * - 库存商品总数量：SUM(currentStockJin)
     * - 库存商品品种数：COUNT 库存记录
     */
    private DashboardResponse.DashboardInventory buildInventoryData(String tenantId) {
        LambdaQueryWrapper<Inventory> inventoryWrapper = new LambdaQueryWrapper<>();
        inventoryWrapper.eq(Inventory::getTenantId, tenantId);
        inventoryWrapper.gt(Inventory::getCurrentStockJin, BigDecimal.ZERO);

        List<Inventory> inventoryList = inventoryMapper.selectList(inventoryWrapper);

        // 库存总价值（分）：汇总所有库存的 totalCostFen
        int totalValueFen = inventoryList.stream()
                .mapToInt(i -> i.getTotalCostFen() != null ? i.getTotalCostFen() : 0)
                .sum();

        // 库存总重量（斤）
        BigDecimal totalStockJin = inventoryList.stream()
                .map(i -> i.getCurrentStockJin() != null ? i.getCurrentStockJin() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 品种数
        int productCount = inventoryList.size();

        DashboardResponse.DashboardInventory inventory = new DashboardResponse.DashboardInventory();
        inventory.setTotalValueYuan(fenToYuan(totalValueFen));
        inventory.setTotalCount(totalStockJin.intValue());
        inventory.setProductCount(productCount);

        return inventory;
    }

    /**
     * "分"转"元"：整除（不保留小数，前端自行格式化）
     * 注意：这里的转换会丢失分的精度，仅用于仪表盘概览显示
     */
    private int fenToYuan(int fen) {
        return fen / 100;
    }
}
