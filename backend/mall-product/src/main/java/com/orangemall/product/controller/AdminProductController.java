package com.orangemall.product.controller;

import com.orangemall.common.Result;
import com.orangemall.product.service.CategoryService;
import com.orangemall.product.service.ProductService;
import com.orangemall.product.vo.CategoryVO;
import com.orangemall.product.vo.SpuDetailVO;
import com.orangemall.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 运营后台：商品上下架、SKU 改价改库存（延迟双删）、类目管理（清二级缓存） */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminProductController {
    private final ProductService productService;
    private final CategoryService categoryService;

    public record StatusReq(String status) {
    }

    public record SkuReq(Integer price, Integer stock) {
    }

    public record CategoryReq(Integer parentId, String name) {
    }

    @GetMapping("/products")
    public Result<List<SpuDetailVO>> products(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status) {
        UserContext.requireAdmin();
        return Result.ok(productService.adminList(keyword, status));
    }

    @PutMapping("/products/{id}/status")
    public Result<Boolean> status(@PathVariable String id, @RequestBody StatusReq req) {
        UserContext.requireAdmin();
        productService.updateStatus(id, req.status());
        return Result.ok();
    }

    @PutMapping("/skus/{id}")
    public Result<Boolean> sku(@PathVariable String id, @RequestBody SkuReq req) {
        UserContext.requireAdmin();
        productService.updateSku(id, req.price(), req.stock());
        return Result.ok();
    }

    @GetMapping("/categories")
    public Result<List<CategoryVO>> categories() {
        UserContext.requireAdmin();
        return Result.ok(categoryService.adminTree());
    }

    @PostMapping("/categories")
    public Result<CategoryVO> create(@RequestBody CategoryReq req) {
        UserContext.requireAdmin();
        return Result.ok(categoryService.create(req.parentId(), req.name()));
    }

    @PutMapping("/categories/{id}")
    public Result<Boolean> rename(@PathVariable String id, @RequestBody CategoryReq req) {
        UserContext.requireAdmin();
        Integer cid = ProductController.intOrNull(id);
        if (cid == null) return Result.fail(9003, "类目不存在");
        categoryService.rename(cid, req.name());
        return Result.ok();
    }

    @DeleteMapping("/categories/{id}")
    public Result<Boolean> delete(@PathVariable String id) {
        UserContext.requireAdmin();
        Integer cid = ProductController.intOrNull(id);
        if (cid != null) categoryService.delete(cid);
        return Result.ok();
    }
}
