package com.saas.fruit.dto.response;

import com.saas.fruit.entity.Customer;
import com.saas.fruit.entity.SalesOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户详情响应DTO
 * 包含客户基本信息以及最近10条销售订单
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetailResponse {

    /** 客户基本信息 */
    private Customer customer;

    /** 最近10条销售订单 */
    private List<SalesOrder> recentOrders;
}
