package com.ljc.dao;

import com.ljc.model.Stock;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockDao {
    Stock getStock(Stock stock);

    int updateCount(Stock stock);
}
