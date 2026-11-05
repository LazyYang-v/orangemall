package com.orangemall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.user.entity.Address;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AddressMapper extends BaseMapper<Address> {
    @Update("UPDATE address SET is_default = 0 WHERE user_id = #{userId} AND is_default = 1")
    int clearDefault(@Param("userId") long userId);
}
