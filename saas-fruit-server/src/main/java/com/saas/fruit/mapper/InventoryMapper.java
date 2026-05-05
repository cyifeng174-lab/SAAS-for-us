package com.saas.fruit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saas.fruit.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    /** 行级锁查询库存（用于并发扣减） */
    @Select("SELECT * FROM inventories WHERE id = #{id} FOR UPDATE")
    Inventory selectByIdForUpdate(@Param("id") Long id);
}
