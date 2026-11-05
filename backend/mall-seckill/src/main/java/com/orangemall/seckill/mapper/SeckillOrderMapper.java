package com.orangemall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    @Select("SELECT * FROM seckill_order WHERE activity_id = #{aid} AND user_id = #{uid}")
    SeckillOrder find(@Param("aid") String activityId, @Param("uid") long userId);

    @Delete("DELETE FROM seckill_order WHERE activity_id = #{aid} AND user_id = #{uid} AND order_id = #{oid}")
    int deleteOne(@Param("aid") String activityId, @Param("uid") long userId, @Param("oid") long orderId);

    @Delete("DELETE FROM seckill_order WHERE activity_id = #{aid}")
    int deleteByActivity(@Param("aid") String activityId);
}
