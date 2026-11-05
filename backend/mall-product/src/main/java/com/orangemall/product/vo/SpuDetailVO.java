package com.orangemall.product.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 前端 Spu（详情 / 后台列表）。favorited 只在详情接口按用户计算，不进缓存 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpuDetailVO extends SpuCardVO {
    private List<SpecGroup> specGroups;
    private List<SkuVO> skus;
    private List<String> detail;
    private List<List<String>> params;
    private Boolean favorited;
}
