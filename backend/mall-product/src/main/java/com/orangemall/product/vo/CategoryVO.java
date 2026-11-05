package com.orangemall.product.vo;

import lombok.Data;

import java.util.List;

/** 前端 Category：树中每个节点都带 children（叶子为空数组），新建接口不带 children */
@Data
public class CategoryVO {
    private Integer id;
    private Integer parentId;
    private String name;
    private String icon;
    private Integer sort;
    private List<CategoryVO> children;
}
