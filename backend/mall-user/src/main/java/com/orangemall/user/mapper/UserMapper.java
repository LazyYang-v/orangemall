package com.orangemall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.user.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {
    /** 加积分（change ≥ 0） */
    @Update("UPDATE user SET points = points + #{change} WHERE id = #{id}")
    int addPoints(@Param("id") long id, @Param("change") int change);

    /** 扣积分：余额不足影响 0 行 */
    @Update("UPDATE user SET points = points - #{amount} WHERE id = #{id} AND points >= #{amount}")
    int deductPoints(@Param("id") long id, @Param("amount") int amount);
}
