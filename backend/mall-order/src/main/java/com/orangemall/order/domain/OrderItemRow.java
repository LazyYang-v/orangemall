package com.orangemall.order.domain;

import lombok.Data;

@Data
public class OrderItemRow {
    private Long id;
    private Long orderId;
    private Long userId;
    private Long skuId;
    private Long spuId;
    private String name;
    private String specText;
    private String art;
    private String tint;
    private int price;
    private int count;
}
