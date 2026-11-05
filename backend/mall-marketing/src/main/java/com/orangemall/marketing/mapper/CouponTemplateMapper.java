package com.orangemall.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.marketing.entity.CouponTemplate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {
    /** 用户领券：DB 层兜底防超发（Redis Lua 已预扣） */
    @Update("UPDATE coupon_template SET received = received + 1 WHERE id = #{id} AND received < total")
    int incReceived(@Param("id") String id);

    /** 系统发券（注册礼包 / 积分兑换）：不受剩余量限制，与 Mock 一致 */
    @Update("UPDATE coupon_template SET received = received + 1 WHERE id = #{id}")
    int incReceivedForce(@Param("id") String id);
}
