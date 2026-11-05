package com.orangemall.product.controller;

import com.orangemall.common.PageResult;
import com.orangemall.common.Result;
import com.orangemall.product.service.CategoryService;
import com.orangemall.product.service.HomeService;
import com.orangemall.product.service.ProductService;
import com.orangemall.product.vo.CategoryVO;
import com.orangemall.product.vo.SearchResult;
import com.orangemall.product.vo.SpuCardVO;
import com.orangemall.product.vo.SpuDetailVO;
import com.orangemall.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProductController {
    private final HomeService homeService;
    private final ProductService productService;
    private final CategoryService categoryService;

    @GetMapping("/home")
    public Result<Map<String, Object>> home() {
        return Result.ok(homeService.home(UserContext.userId()));
    }

    @GetMapping("/home/recommend")
    public Result<PageResult<SpuCardVO>> recommend(@RequestParam(required = false) String page,
                                                   @RequestParam(required = false) String size) {
        return Result.ok(productService.recommend(intOr(page, 1), intOr(size, 10)));
    }

    @GetMapping("/categories/tree")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    @GetMapping("/products")
    public Result<SearchResult> search(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String categoryId,
                                       @RequestParam(required = false) String brandId,
                                       @RequestParam(required = false) String minPrice,
                                       @RequestParam(required = false) String maxPrice,
                                       @RequestParam(required = false) String sort,
                                       @RequestParam(required = false) String isNew,
                                       @RequestParam(required = false) String page,
                                       @RequestParam(required = false) String size) {
        int p = Math.max(1, intOr(page, 1));
        int s = Math.min(100, Math.max(1, intOr(size, 20)));
        return Result.ok(productService.search(keyword, intOrNull(categoryId), intOrNull(brandId),
                doubleOrNull(minPrice), doubleOrNull(maxPrice), sort, "true".equals(isNew) || "1".equals(isNew), p, s));
    }

    @GetMapping("/products/{id}")
    public Result<SpuDetailVO> detail(@PathVariable String id) {
        return Result.ok(productService.detail(id, UserContext.userId()));
    }

    @PostMapping("/products/{id}/favorite")
    public Result<Boolean> favorite(@PathVariable String id) {
        return Result.ok(productService.toggleFavorite(UserContext.requireUser(), id));
    }

    @GetMapping("/favorites")
    public Result<List<SpuCardVO>> favorites() {
        return Result.ok(productService.favorites(UserContext.requireUser()));
    }

    /* 查询参数宽松解析：空串 / 非法值按未传处理（与 Mock 的 `if (q.xxx)` 语义一致） */
    static int intOr(String v, int def) {
        Integer i = intOrNull(v);
        return i == null ? def : i;
    }

    static Integer intOrNull(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return (int) Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Double doubleOrNull(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            double d = Double.parseDouble(v.trim());
            return d == 0 ? null : d; // Mock: if (q.minPrice) —— 0 视为未传
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
