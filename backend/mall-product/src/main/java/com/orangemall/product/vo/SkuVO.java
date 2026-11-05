package com.orangemall.product.vo;

import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class SkuVO {
    private Long id;
    private Long spuId;
    private LinkedHashMap<String, String> specs;
    private int price;
    private int originalPrice;
    private int stock;
    private int sales;
}
