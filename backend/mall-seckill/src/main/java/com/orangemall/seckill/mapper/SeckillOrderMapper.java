package com.orangemall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    @Select("SELECT * FROM seckill_order WHERE activity_id = #{aid} AND user_id = #{uid}")
    SeckillOrder find(@Param("aid") String activityId, @Param("uid") long userId);

    @Delete("DELETE FROM seckill_order WHERE activity_id = #{aid} AND user_id = #{uid} AND order_id = #{oid}")
    int deleteOne(@Param("aid") String activityId, @Param("uid") long userId, @Param("oid") long orderId);

    @Delete("DELETE FROM seckill_order WHERE activity_id = #{aid}")
    int deleteByActivity(@Param("aid") String activityId);

    /**
     * 合并写入成交记录（OM-025）：uk_activity_user 冲突的行被忽略，返回值 = 实际新插入行数（MySQL INSERT IGNORE 语义）。
     * 只读取 rows 中每个元素的 userId / orderId。
     */
    @Insert({"<script>INSERT IGNORE INTO seckill_order (activity_id, user_id, order_id) VALUES ",
            "<foreach collection='rows' item='r' separator=','>(#{aid}, #{r.userId}, #{r.orderId})</foreach></script>"})
    int insertIgnore(@Param("aid") String activityId, @Param("rows") List<SeckillOrder> rows);
}
