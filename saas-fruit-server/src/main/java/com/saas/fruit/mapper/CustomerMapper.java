package com.saas.fruit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saas.fruit.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {

    /** 更新客户销售统计（累计销售、累计收款、欠款、订单数） */
    @Update("UPDATE customers SET total_sales_fen = total_sales_fen + #{totalSalesFen}, " +
            "total_paid_fen = total_paid_fen + #{totalPaidFen}, " +
            "total_debt_fen = total_debt_fen + #{totalDebtFen}, " +
            "order_count = order_count + 1, " +
            "last_order_time = #{lastOrderTime}, " +
            "first_order_time = IF(first_order_time = 0, #{lastOrderTime}, first_order_time), " +
            "update_time = #{updateTime} " +
            "WHERE id = #{customerId}")
    int updateSalesStats(@Param("customerId") Long customerId,
                         @Param("totalSalesFen") Integer totalSalesFen,
                         @Param("totalPaidFen") Integer totalPaidFen,
                         @Param("totalDebtFen") Integer totalDebtFen,
                         @Param("lastOrderTime") Long lastOrderTime,
                         @Param("updateTime") Long updateTime);

    /** 更新客户收款统计 */
    @Update("UPDATE customers SET total_debt_fen = #{totalDebtFen}, " +
            "total_paid_fen = total_paid_fen + #{paidAmountFen}, " +
            "update_time = #{updateTime} " +
            "WHERE id = #{customerId}")
    int updateReceiveStats(@Param("customerId") Long customerId,
                           @Param("totalDebtFen") Integer totalDebtFen,
                           @Param("paidAmountFen") Integer paidAmountFen,
                           @Param("updateTime") Long updateTime);
}
