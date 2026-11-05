package com.orangemall.product.vo;

import java.util.List;

/** PageResult<SpuCard> & { brands: Brand[] } */
public record SearchResult(List<SpuCardVO> records, int total, int page, int size, List<BrandVO> brands) {
}
