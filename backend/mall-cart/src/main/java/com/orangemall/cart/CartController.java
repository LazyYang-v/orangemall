package com.orangemall.cart;

import com.orangemall.cart.CartService.CartCheckIssue;
import com.orangemall.cart.CartService.CartItem;
import com.orangemall.common.Result;
import com.orangemall.web.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class CartController {
    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping("/cart")
    public Result<List<CartItem>> list() {
        return Result.ok(cart.list(UserContext.requireUser()));
    }

    /** 未登录购物车（前端 localStorage）调此接口补全商品信息 */
    @PostMapping("/cart/preview")
    public Result<List<CartItem>> preview(@RequestBody Map<String, List<Map<String, Object>>> body) {
        return Result.ok(cart.preview(body.getOrDefault("items", List.of())));
    }

    @PostMapping("/cart/items")
    public Result<Integer> add(@RequestBody Map<String, Object> body) {
        return Result.ok(cart.add(UserContext.requireUser(),
                String.valueOf(body.get("skuId")), ((Number) body.getOrDefault("count", 1)).intValue()));
    }

    @PutMapping("/cart/items/{skuId}")
    public Result<Boolean> update(@PathVariable String skuId, @RequestBody Map<String, Object> body) {
        Integer count = body.get("count") == null ? null : ((Number) body.get("count")).intValue();
        Boolean checked = body.get("checked") == null ? null : (Boolean) body.get("checked");
        return Result.ok(cart.update(UserContext.requireUser(), skuId, count, checked));
    }

    @PutMapping("/cart/check-all")
    public Result<Boolean> checkAll(@RequestBody Map<String, Object> body) {
        return Result.ok(cart.checkAll(UserContext.requireUser(), Boolean.TRUE.equals(body.get("checked"))));
    }

    @DeleteMapping("/cart/items")
    public Result<Boolean> remove(@RequestBody Map<String, List<String>> body) {
        return Result.ok(cart.remove(UserContext.requireUser(), body.getOrDefault("skuIds", List.of())));
    }

    @PostMapping("/cart/merge")
    public Result<Integer> merge(@RequestBody Map<String, List<Map<String, Object>>> body) {
        return Result.ok(cart.merge(UserContext.requireUser(), body.getOrDefault("items", List.of())));
    }

    @PostMapping("/cart/check")
    public Result<List<CartCheckIssue>> check(@RequestBody Map<String, List<Map<String, Object>>> body) {
        return Result.ok(cart.check(body.getOrDefault("items", List.of())));
    }
}
