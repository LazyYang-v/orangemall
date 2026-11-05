package com.orangemall.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.pay.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayMapper extends BaseMapper<Payment> {
}
