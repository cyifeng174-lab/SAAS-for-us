package com.saas.fruit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saas.fruit.entity.Purchase;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PurchaseMapper extends BaseMapper<Purchase> {
}
