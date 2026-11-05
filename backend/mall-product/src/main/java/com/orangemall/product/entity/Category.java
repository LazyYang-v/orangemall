package com.orangemall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("category")
public class Category {
    private Integer id;
    private Integer parentId;
    private String name;
    private String icon;
    private Integer sort;
}
