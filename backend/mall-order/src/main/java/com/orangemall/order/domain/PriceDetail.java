package com.orangemall.order.domain;

/** 与前端 OrderPriceDetail 一致（单位：分） */
public record PriceDetail(int goodsAmount, int freight, int fullReduction, int couponDeduct, int pointsDeduct,
                          int seckillDeduct, int payAmount) {
}
