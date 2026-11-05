package com.orangemall.product.vo;

import lombok.Data;

import java.util.List;

/** 前端 SpuCard = Omit<Spu, 'specGroups' | 'skus' | 'detail' | 'params'> */
@Data
public class SpuCardVO {
    private Long id;
    private String name;
    private String subtitle;
    private Integer categoryId;
    private List<String> categoryPath;
    private Integer brandId;
    private String brandName;
    private String art;
    private String tint;
    private String imageUrl;
    private int minPrice;
    private int maxOriginalPrice;
    private int sales;
    private double rating;
    private List<String> tags;
    private String status;
    /** 仅新品输出 true，否则不输出（与 Mock 的 isNew?: boolean 一致） */
    private Boolean isNew;
    private String shopName;

    public void copyTo(SpuCardVO t) {
        t.id = id; t.name = name; t.subtitle = subtitle; t.categoryId = categoryId; t.categoryPath = categoryPath;
        t.brandId = brandId; t.brandName = brandName; t.art = art; t.tint = tint; t.imageUrl = imageUrl;
        t.minPrice = minPrice; t.maxOriginalPrice = maxOriginalPrice; t.sales = sales; t.rating = rating;
        t.tags = tags; t.status = status; t.isNew = isNew; t.shopName = shopName;
    }

    public SpuCardVO toCard() {
        SpuCardVO c = new SpuCardVO();
        copyTo(c);
        return c;
    }
}
