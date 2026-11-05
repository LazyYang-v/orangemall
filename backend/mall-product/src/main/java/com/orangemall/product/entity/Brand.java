package com.orangemall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("brand")
public class Brand {
    private Integer id;
    private String name;
    private String shopName;
}
