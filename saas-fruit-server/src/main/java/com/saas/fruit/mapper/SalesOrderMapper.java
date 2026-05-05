package com.saas.fruit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saas.fruit.entity.SalesOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrder> {
}
