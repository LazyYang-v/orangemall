package com.orangemall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sku")
public class Sku {
    private Long id;
    private Long spuId;
    /** JSON {规格名: 规格值}，保持规格组顺序 */
    private String specs;
    private Integer price;
    private Integer originalPrice;
    private Integer stock;
    private Integer sales;
}
