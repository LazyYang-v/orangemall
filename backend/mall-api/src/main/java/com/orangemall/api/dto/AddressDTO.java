package com.orangemall.api.dto;

/** 收货地址（与前端 Address 字段一致，订单中作为快照保存） */
public record AddressDTO(Long id, String receiver, String phone, String province, String city, String district,
                         String detail, boolean isDefault) {
}
