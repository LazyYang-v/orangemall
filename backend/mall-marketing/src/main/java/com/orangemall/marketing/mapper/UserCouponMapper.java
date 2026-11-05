package com.orangemall.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.marketing.entity.UserCoupon;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface UserCouponMapper extends BaseMapper<UserCoupon> {
    @Update("UPDATE user_coupon SET status = 'EXPIRED' WHERE user_id = #{userId} AND status = 'UNUSED' AND valid_to < #{now}")
    int expire(@Param("userId") long userId, @Param("now") Instant now);

    @Update("UPDATE user_coupon SET status = 'LOCKED', order_id = #{orderId} WHERE id = #{id} AND user_id = #{userId} AND status = 'UNUSED' AND valid_to > #{now}")
    int lock(@Param("id") long id, @Param("userId") long userId, @Param("orderId") Long orderId, @Param("now") Instant now);

    @Update("<script>UPDATE user_coupon SET status = 'UNUSED', order_id = NULL WHERE id = #{id} AND status = 'LOCKED'"
            + "<if test='userId != null'> AND user_id = #{userId}</if><if test='orderId != null'> AND order_id = #{orderId}</if></script>")
    int release(@Param("id") long id, @Param("userId") Long userId, @Param("orderId") Long orderId);

    @Update("<script>UPDATE user_coupon SET status = 'USED' WHERE id = #{id} AND status = 'LOCKED'"
            + "<if test='userId != null'> AND user_id = #{userId}</if><if test='orderId != null'> AND order_id = #{orderId}</if></script>")
    int use(@Param("id") long id, @Param("userId") Long userId, @Param("orderId") Long orderId);

    /** 用户各模板已领张数（receivedByMe） */
    @Select("SELECT template_id AS k, COUNT(*) AS c FROM user_coupon WHERE user_id = #{userId} GROUP BY template_id")
    List<Map<String, Object>> countByTemplate(@Param("userId") long userId);

    /** 模板维度各用户已领张数（预热 Redis 限领计数） */
    @Select("SELECT user_id AS k, COUNT(*) AS c FROM user_coupon WHERE template_id = #{templateId} GROUP BY user_id")
    List<Map<String, Object>> countByUser(@Param("templateId") String templateId);
}
