package com.orangemall.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.api.client.ProductInnerClient;
import com.orangemall.api.dto.SkuInfo;
import com.orangemall.common.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车：Redis Hash cart:{userId}，field=skuId，value=JSON {count, checked, addedAt}。
 * 商品信息（实时价、库存、上下架）不落购物车，展示时经商品服务批量补全 —— 价格变化天然可见。
 */
@Service
public class CartService {
    static final int MAX_PER_ITEM = 99;

    private final StringRedisTemplate redis;
    private final ProductInnerClient product;
    private final ObjectMapper om;

    /** 与前端 CartItem 字段一一对应 */
    public record CartItem(String skuId, String spuId, String name, String specText, String art, String tint,
                           int price, int originalPrice, int count, boolean checked, int stock,
                           String status, String shopName) {
    }

    /** 结算前预校验问题项 */
    public record CartCheckIssue(String skuId, String reason, String message) {
    }

    record CartField(int count, boolean checked, long addedAt) {
    }

    public CartService(StringRedisTemplate redis, ProductInnerClient product, ObjectMapper om) {
        this.redis = redis;
        this.product = product;
        this.om = om;
    }

    private String key(long userId) {
        return "cart:" + userId;
    }

    private Map<String, CartField> readAll(long userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(key(userId));
        Map<String, CartField> out = new LinkedHashMap<>();
        entries.forEach((k, v) -> {
            try {
                out.put((String) k, om.readValue((String) v, CartField.class));
            } catch (Exception ignored) {
            }
        });
        return out;
    }

    private void write(long userId, String skuId, CartField f) {
        try {
            redis.opsForHash().put(key(userId), skuId, om.writeValueAsString(f));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void writeAll(long userId, Map<String, CartField> fields) {
        if (fields.isEmpty()) {
            redis.delete(key(userId));
            return;
        }
        Map<String, String> raw = new HashMap<>();
        fields.forEach((skuId, f) -> {
            try {
                raw.put(skuId, om.writeValueAsString(f));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        redis.opsForHash().putAll(key(userId), raw);
    }

    /** 补全商品信息并组装 CartItem（订单按 addedAt 倒序 = mock 的加入顺序倒序） */
    private List<CartItem> decorate(Map<String, CartField> fields) {
        if (fields.isEmpty()) return List.of();
        Map<String, SkuInfo> skus = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        fields.keySet().forEach(k -> ids.add(Long.parseLong(k)));
        for (SkuInfo s : product.skus(ids).unwrap()) skus.put(String.valueOf(s.skuId()), s);
        List<CartItem> items = new ArrayList<>();
        fields.entrySet().stream()
                .filter(e -> skus.containsKey(e.getKey()))
                .sorted((a, b) -> Long.compare(b.getValue().addedAt(), a.getValue().addedAt()))
                .forEach(e -> {
                    SkuInfo s = skus.get(e.getKey());
                    CartField f = e.getValue();
                    String status = !"ON_SALE".equals(s.spuStatus()) ? "OFF_SALE"
                            : s.stock() <= 0 ? "NO_STOCK" : "NORMAL";
                    items.add(new CartItem(String.valueOf(s.skuId()), String.valueOf(s.spuId()), s.name(),
                            s.specText(), s.art(), s.tint(), s.price(), s.originalPrice(),
                            f.count(), f.checked() && "NORMAL".equals(status), s.stock(), status, s.shopName()));
                });
        return items;
    }

    public List<CartItem> list(long userId) {
        return decorate(readAll(userId));
    }

    public List<CartItem> preview(List<Map<String, Object>> rawItems) {
        Map<String, CartField> fields = new LinkedHashMap<>();
        for (Map<String, Object> it : rawItems) {
            fields.put(String.valueOf(it.get("skuId")),
                    new CartField(((Number) it.getOrDefault("count", 1)).intValue(),
                            Boolean.TRUE.equals(it.get("checked")), 0));
        }
        return decorate(fields);
    }

    public int add(long userId, String skuId, int count) {
        SkuInfo sku = findSku(skuId);
        Map<String, CartField> all = readAll(userId);
        int next = all.getOrDefault(skuId, new CartField(0, true, 0)).count() + count;
        if (next > sku.stock()) throw new BizException(3001, "库存不足，最多可购买 " + sku.stock() + " 件");
        if (next > MAX_PER_ITEM) throw new BizException(3002, "单件商品最多购买 99 件");
        all.put(skuId, new CartField(next, true, System.currentTimeMillis()));
        writeAll(userId, all);
        return all.size();
    }

    public boolean update(long userId, String skuId, Integer count, Boolean checked) {
        Map<String, CartField> all = readAll(userId);
        CartField f = all.get(skuId);
        if (f == null) throw new BizException(3003, "购物车中不存在该商品");
        if (count != null) {
            int stock = findSku(skuId).stock();
            if (count > stock) throw new BizException(3001, "库存不足，最多可购买 " + stock + " 件");
            f = new CartField(Math.max(1, Math.min(MAX_PER_ITEM, count)), f.checked(), f.addedAt());
        }
        if (checked != null) f = new CartField(f.count(), checked, f.addedAt());
        all.put(skuId, f);
        writeAll(userId, all);
        return true;
    }

    public boolean checkAll(long userId, boolean checked) {
        Map<String, CartField> all = readAll(userId);
        all.replaceAll((k, f) -> new CartField(f.count(), checked, f.addedAt()));
        writeAll(userId, all);
        return true;
    }

    public boolean remove(long userId, List<String> skuIds) {
        if (skuIds != null && !skuIds.isEmpty()) redis.opsForHash().delete(key(userId), skuIds.toArray());
        return true;
    }

    /** 登录后合并游客购物车：数量相加，封顶库存与 99 */
    public int merge(long userId, List<Map<String, Object>> rawItems) {
        Map<String, CartField> all = readAll(userId);
        for (Map<String, Object> it : rawItems) {
            String skuId = String.valueOf(it.get("skuId"));
            int count = ((Number) it.getOrDefault("count", 1)).intValue();
            boolean checked = Boolean.TRUE.equals(it.get("checked"));
            int stock = findSku(skuId).stock();
            int merged = Math.min(Math.min(stock > 0 ? stock : 1, MAX_PER_ITEM),
                    all.getOrDefault(skuId, new CartField(0, true, 0)).count() + count);
            all.put(skuId, new CartField(merged, checked, System.currentTimeMillis()));
        }
        writeAll(userId, all);
        return all.size();
    }

    public List<CartCheckIssue> check(List<Map<String, Object>> rawItems) {
        List<CartCheckIssue> issues = new ArrayList<>();
        for (Map<String, Object> it : rawItems) {
            String skuId = String.valueOf(it.get("skuId"));
            int count = ((Number) it.getOrDefault("count", 1)).intValue();
            SkuInfo s = findSkuQuiet(skuId);
            if (s == null || !"ON_SALE".equals(s.spuStatus())) issues.add(new CartCheckIssue(skuId, "OFF_SALE", "商品已下架"));
            else if (s.stock() <= 0) issues.add(new CartCheckIssue(skuId, "NO_STOCK", "商品已售罄"));
            else if (s.stock() < count) issues.add(new CartCheckIssue(skuId, "STOCK_NOT_ENOUGH", "库存仅剩 " + s.stock() + " 件"));
        }
        return issues;
    }

    private SkuInfo findSku(String skuId) {
        SkuInfo s = findSkuQuiet(skuId);
        if (s == null) throw new BizException(2001, "商品不存在");
        return s;
    }

    private SkuInfo findSkuQuiet(String skuId) {
        try {
            long id = Long.parseLong(skuId);
            List<SkuInfo> list = product.skus(List.of(id)).unwrap();
            return list.isEmpty() ? null : list.get(0);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }
}
