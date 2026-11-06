package com.orangemall.api.dto;

/** 一条库存流水（mall_product.stock_ledger）：op = DEDUCT / RESTORE，qty 为正数 */
public record LedgerEntry(Long orderId, Long skuId, String op, int qty) {
}
