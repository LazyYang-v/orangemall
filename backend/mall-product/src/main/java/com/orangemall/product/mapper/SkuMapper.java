package com.orangemall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.product.entity.Sku;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SkuMapper extends BaseMapper<Sku> {

    /** 条件扣减：WHERE stock >= n 保证不超卖（行锁串行化），影响 0 行即库存不足 */
    @Update("UPDATE sku SET stock = stock - #{n}, sales = sales + #{n} WHERE id = #{id} AND stock >= #{n}")
    int deduct(@Param("id") long id, @Param("n") int n);

    /** 回补库存，销量同步回退（不低于 0） */
    @Update("UPDATE sku SET stock = stock + #{n}, sales = GREATEST(sales - #{n}, 0) WHERE id = #{id}")
    int restore(@Param("id") long id, @Param("n") int n);
}
