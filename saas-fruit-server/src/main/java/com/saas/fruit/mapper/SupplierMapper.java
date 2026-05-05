package com.saas.fruit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saas.fruit.entity.Supplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {

    /** 更新供应商采购统计 */
    @Update("UPDATE suppliers SET total_debt_fen = total_debt_fen + #{debtAmountFen}, " +
            "total_purchase_fen = total_purchase_fen + #{totalPurchaseFen}, " +
            "total_paid_fen = total_paid_fen + #{paidAmountFen}, " +
            "purchase_count = purchase_count + 1, " +
            "last_purchase_time = #{lastPurchaseTime}, " +
            "first_purchase_time = IF(first_purchase_time = 0, #{lastPurchaseTime}, first_purchase_time), " +
            "update_time = #{updateTime} " +
            "WHERE id = #{supplierId}")
    int updatePurchaseStats(@Param("supplierId") Long supplierId,
                            @Param("totalPurchaseFen") Integer totalPurchaseFen,
                            @Param("paidAmountFen") Integer paidAmountFen,
                            @Param("debtAmountFen") Integer debtAmountFen,
                            @Param("lastPurchaseTime") Long lastPurchaseTime,
                            @Param("updateTime") Long updateTime);
}
