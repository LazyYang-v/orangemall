package com.orangemall.api.dto;

import java.util.List;

/** 普通订单库存操作：orderId 是库存流水的幂等键（同一订单同一 SKU 只扣一次、只回补一次） */
public record StockOpReq(Long orderId, List<StockItem> items) {
}
