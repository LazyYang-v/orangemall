package com.orangemall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.user.entity.PointsGoods;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PointsGoodsMapper extends BaseMapper<PointsGoods> {
    /** 乐观扣库存：已兑完影响 0 行 */
    @Update("UPDATE points_goods SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
    int deductStock(@Param("id") String id);
}
