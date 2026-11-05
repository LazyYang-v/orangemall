package com.orangemall.api.dto;

/**
 * SKU 快照（商品服务 → 购物车/订单/秒杀）。
 * spuStatus: ON_SALE / OFF_SALE；rootCategoryId 用于优惠券适用范围判断。
 */
public record SkuInfo(Long skuId, Long spuId, String name, String specText, String art, String tint,
                      int price, int originalPrice, int stock, String spuStatus, String shopName,
                      Integer categoryId, Integer rootCategoryId) {
}
