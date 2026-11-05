package com.orangemall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.seckill.entity.SeckillActivity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /** 乐观锁扣减：影响行数 0 即 DB 库存兜底拦截 */
    @Update("UPDATE seckill_activity SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
    int decrStock(@Param("id") String id);

    @Update("UPDATE seckill_activity SET stock = stock + 1 WHERE id = #{id} AND stock < total_stock")
    int incrStock(@Param("id") String id);

    @Select("SELECT stock FROM seckill_activity WHERE id = #{id}")
    Integer stockOf(@Param("id") String id);

    /** 首次自动预热：只执行一次（warmed = 0 → 1） */
    @Update("UPDATE seckill_activity SET stock = #{stock}, warmed = 1 WHERE id = #{id} AND warmed = 0")
    int firstWarm(@Param("id") String id, @Param("stock") int stock);

    /** 后台手动预热：库存重置为总量 */
    @Update("UPDATE seckill_activity SET stock = total_stock, warmed = 1 WHERE id = #{id}")
    int resetStock(@Param("id") String id);

    @Insert("INSERT IGNORE INTO seckill_activity (id, session_id, sku_id, spu_id, name, art, tint, seckill_price, original_price, "
            + "total_stock, stock, limit_per_user, warmed, start_at, end_at) VALUES (#{a.id}, #{a.sessionId}, #{a.skuId}, #{a.spuId}, "
            + "#{a.name}, #{a.art}, #{a.tint}, #{a.seckillPrice}, #{a.originalPrice}, #{a.totalStock}, #{a.stock}, #{a.limitPerUser}, "
            + "0, #{a.startAt}, #{a.endAt})")
    int insertIgnore(@Param("a") SeckillActivity a);
}
