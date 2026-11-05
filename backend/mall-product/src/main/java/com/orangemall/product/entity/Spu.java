package com.orangemall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** tags / spec_groups / detail / params 为 JSON 文本列，出参时解析为真正的 JSON 数组 */
@Data
@TableName("spu")
public class Spu {
    private Long id;
    private String name;
    private String subtitle;
    private Integer categoryId;
    private Integer brandId;
    private String art;
    private String tint;
    private String imageUrl;
    private Integer minPrice;
    private Integer maxOriginalPrice;
    private Integer sales;
    private BigDecimal rating;
    private String tags;
    private String status;
    private String specGroups;
    private String detail;
    private String params;
    private Integer isNew;
    private String shopName;
}
