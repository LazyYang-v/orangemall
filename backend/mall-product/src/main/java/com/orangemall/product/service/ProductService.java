package com.orangemall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.orangemall.common.BizException;
import com.orangemall.common.PageResult;
import com.orangemall.product.entity.*;
import com.orangemall.product.mapper.*;
import com.orangemall.product.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {
    private static final Pattern WS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    /** 搜索别名（与 Mock 一致） */
    private static final Map<String, String> ALIAS = Map.of("iphone17", "phone 17", "双11", "");

    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final BrandMapper brandMapper;
    private final FavoriteMapper favoriteMapper;
    private final CategoryService categoryService;
    private final ProductCacheService cache;
    private final ObjectMapper mapper;
    private final Cache<String, Map<Integer, Brand>> brandCache = Caffeine.newBuilder()
            .maximumSize(1).expireAfterWrite(Duration.ofMinutes(5)).build();

    /* ============================== 组装 ============================== */

    Map<Integer, Brand> brands() {
        return brandCache.get("all", k -> brandMapper.selectList(null).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b, (a, b) -> a, HashMap::new)));
    }

    private <T> T json(String s, TypeReference<T> t, T def) {
        if (s == null || s.isBlank()) return def;
        try {
            return mapper.readValue(s, t);
        } catch (Exception e) {
            return def;
        }
    }

    List<String> strings(String s) {
        return json(s, new TypeReference<List<String>>() {}, new ArrayList<>());
    }

    LinkedHashMap<String, String> specs(String s) {
        return json(s, new TypeReference<LinkedHashMap<String, String>>() {}, new LinkedHashMap<>());
    }

    static String specText(Map<String, String> specs) {
        return String.join(" / ", specs.values());
    }

    private void fillCard(Spu s, SpuCardVO c) {
        Brand b = brands().get(s.getBrandId());
        c.setId(s.getId());
        c.setName(s.getName());
        c.setSubtitle(s.getSubtitle());
        c.setCategoryId(s.getCategoryId());
        c.setCategoryPath(categoryService.path(s.getCategoryId()));
        c.setBrandId(s.getBrandId());
        c.setBrandName(b == null ? "" : b.getName());
        c.setArt(s.getArt());
        c.setTint(s.getTint());
        c.setImageUrl(s.getImageUrl());
        c.setMinPrice(s.getMinPrice());
        c.setMaxOriginalPrice(s.getMaxOriginalPrice());
        c.setSales(s.getSales());
        c.setRating(s.getRating() == null ? 5.0 : s.getRating().doubleValue());
        c.setTags(strings(s.getTags()));
        c.setStatus(s.getStatus());
        c.setIsNew(s.getIsNew() != null && s.getIsNew() == 1 ? Boolean.TRUE : null);
        c.setShopName(s.getShopName());
    }

    SpuCardVO toCard(Spu s) {
        SpuCardVO c = new SpuCardVO();
        fillCard(s, c);
        return c;
    }

    SkuVO toSku(Sku k) {
        SkuVO v = new SkuVO();
        v.setId(k.getId());
        v.setSpuId(k.getSpuId());
        v.setSpecs(specs(k.getSpecs()));
        v.setPrice(k.getPrice());
        v.setOriginalPrice(k.getOriginalPrice());
        v.setStock(k.getStock());
        v.setSales(k.getSales());
        return v;
    }

    SpuDetailVO toDetail(Spu s, List<Sku> skus) {
        SpuDetailVO d = new SpuDetailVO();
        fillCard(s, d);
        d.setSpecGroups(json(s.getSpecGroups(), new TypeReference<List<SpecGroup>>() {}, new ArrayList<>()));
        d.setSkus(skus.stream().sorted(Comparator.comparing(Sku::getId)).map(this::toSku).toList());
        d.setDetail(strings(s.getDetail()));
        d.setParams(json(s.getParams(), new TypeReference<List<List<String>>>() {}, new ArrayList<>()));
        return d;
    }

    /** 卡片字段（不查大文本列） */
    private LambdaQueryWrapper<Spu> cardQuery() {
        return new LambdaQueryWrapper<Spu>().select(Spu::getId, Spu::getName, Spu::getSubtitle, Spu::getCategoryId,
                Spu::getBrandId, Spu::getArt, Spu::getTint, Spu::getImageUrl, Spu::getMinPrice, Spu::getMaxOriginalPrice,
                Spu::getSales, Spu::getRating, Spu::getTags, Spu::getStatus, Spu::getIsNew, Spu::getShopName);
    }

    public static Long parseId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /* ============================== 首页 ============================== */

    public SpuCardVO newArrival() {
        Spu s = spuMapper.selectById(1006L);
        if (s == null) s = spuMapper.selectOne(cardQuery().eq(Spu::getIsNew, 1).orderByDesc(Spu::getId).last("LIMIT 1"));
        if (s == null) s = spuMapper.selectOne(cardQuery().orderByAsc(Spu::getId).last("LIMIT 1"));
        return s == null ? null : toCard(s);
    }

    /** 推荐流：按销量倒序 + 按页轮换（与 Mock 一致，total 固定 200 模拟无限流） */
    public PageResult<SpuCardVO> recommend(int page, int size) {
        List<Spu> sorted = spuMapper.selectList(cardQuery().eq(Spu::getStatus, "ON_SALE")
                .orderByDesc(Spu::getSales).orderByAsc(Spu::getId));
        int shift = Math.min(Math.max(page - 1, 0), sorted.size());
        List<Spu> rotated = new ArrayList<>(sorted.subList(shift, sorted.size()));
        rotated.addAll(sorted.subList(0, shift));
        List<SpuCardVO> records = rotated.stream().limit(Math.max(size, 0)).map(this::toCard).toList();
        return new PageResult<>(records, 200, page, size);
    }

    /* ============================== 搜索 ============================== */

    public SearchResult search(String keyword, Integer categoryId, Integer brandId, Double minPrice, Double maxPrice,
                               String sort, Boolean isNew, int page, int size) {
        LambdaQueryWrapper<Spu> q = cardQuery().eq(Spu::getStatus, "ON_SALE");
        if (categoryId != null) q.in(Spu::getCategoryId, categoryService.descendantIds(categoryId));
        if (brandId != null) q.eq(Spu::getBrandId, brandId);
        if (minPrice != null) q.ge(Spu::getMinPrice, minPrice * 100);
        if (maxPrice != null) q.le(Spu::getMinPrice, maxPrice * 100);
        if (Boolean.TRUE.equals(isNew)) q.eq(Spu::getIsNew, 1);
        q.orderByAsc(Spu::getId);
        List<SpuCardVO> list = spuMapper.selectList(q).stream().map(this::toCard).collect(Collectors.toCollection(ArrayList::new));

        if (keyword != null && !keyword.isEmpty()) {
            String kw = WS.matcher(keyword.toLowerCase(Locale.ROOT)).replaceAll("");
            String k2 = WS.matcher(ALIAS.getOrDefault(kw, kw)).replaceAll("");
            list.removeIf(s -> {
                List<String> parts = new ArrayList<>();
                parts.add(s.getName());
                parts.add(s.getSubtitle());
                parts.add(s.getBrandName());
                parts.addAll(s.getCategoryPath());
                parts.addAll(s.getTags());
                String hay = WS.matcher(String.join(" ", parts).toLowerCase(Locale.ROOT)).replaceAll("");
                return !hay.contains(k2);
            });
        }
        Comparator<SpuCardVO> sorter = switch (sort == null ? "" : sort) {
            case "sales" -> Comparator.comparingInt(SpuCardVO::getSales).reversed();
            case "price_asc" -> Comparator.comparingInt(SpuCardVO::getMinPrice);
            case "price_desc" -> Comparator.comparingInt(SpuCardVO::getMinPrice).reversed();
            case "new" -> Comparator.comparingInt((SpuCardVO s) -> Boolean.TRUE.equals(s.getIsNew()) ? 0 : 1);
            default -> null;
        };
        if (sorter != null) list.sort(sorter); // List.sort 稳定，与 JS Array.sort 一致

        LinkedHashMap<Integer, String> facet = new LinkedHashMap<>();
        list.forEach(s -> facet.putIfAbsent(s.getBrandId(), s.getBrandName()));
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(list.size(), from + size);
        List<SpuCardVO> records = from >= to ? List.of() : List.copyOf(list.subList(from, to));
        return new SearchResult(records, list.size(), page, size,
                facet.entrySet().stream().map(e -> new BrandVO(e.getKey(), e.getValue())).toList());
    }

    /* ============================== 详情 / 收藏 ============================== */

    public SpuDetailVO loadDetail(long spuId) {
        Spu s = spuMapper.selectById(spuId);
        if (s == null) return null;
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>().eq(Sku::getSpuId, spuId));
        return toDetail(s, skus);
    }

    public SpuDetailVO detail(String idStr, Long userId) {
        Long id = parseId(idStr);
        if (id == null) throw BizException.of(2001, "商品不存在或已下架");
        SpuDetailVO d = cache.get(id, this::loadDetail);
        // 类目路径以实时类目为准（类目改名无需清全部详情缓存）；收藏状态按用户计算，不进缓存
        d.setCategoryPath(categoryService.path(d.getCategoryId()));
        d.setFavorited(userId != null && favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getSpuId, id)) > 0);
        return d;
    }

    /** 切换收藏，返回切换后的状态 */
    public boolean toggleFavorite(long userId, String idStr) {
        Long spuId = parseId(idStr);
        if (spuId == null || spuMapper.selectCount(new LambdaQueryWrapper<Spu>().eq(Spu::getId, spuId)) == 0)
            throw BizException.of(2001, "商品不存在");
        int removed = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getSpuId, spuId));
        if (removed > 0) return false;
        Favorite f = new Favorite();
        f.setUserId(userId);
        f.setSpuId(spuId);
        try {
            favoriteMapper.insert(f);
        } catch (DuplicateKeyException ignored) {
            // 并发重复点击：唯一索引 uk_user_spu 兜底
        }
        return true;
    }

    public List<SpuCardVO> favorites(long userId) {
        List<Long> ids = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId).orderByAsc(Favorite::getId))
                .stream().map(Favorite::getSpuId).toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, Spu> spus = spuMapper.selectList(cardQuery().in(Spu::getId, ids)).stream()
                .collect(Collectors.toMap(Spu::getId, s -> s));
        return ids.stream().map(spus::get).filter(Objects::nonNull).map(this::toCard).toList();
    }

    /* ============================== 后台 ============================== */

    public List<SpuDetailVO> adminList(String keyword, String status) {
        LambdaQueryWrapper<Spu> q = new LambdaQueryWrapper<Spu>().orderByAsc(Spu::getId);
        if (status != null && !status.isEmpty()) q.eq(Spu::getStatus, status);
        List<Spu> spus = spuMapper.selectList(q);
        if (keyword != null && !keyword.isEmpty())
            spus = spus.stream().filter(s -> s.getName().contains(keyword) || String.valueOf(s.getId()).contains(keyword)).toList();
        if (spus.isEmpty()) return List.of();
        Map<Long, List<Sku>> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                        .in(Sku::getSpuId, spus.stream().map(Spu::getId).toList()))
                .stream().collect(Collectors.groupingBy(Sku::getSpuId));
        return spus.stream().map(s -> toDetail(s, skus.getOrDefault(s.getId(), List.of()))).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String idStr, String status) {
        if (!"ON_SALE".equals(status) && !"OFF_SALE".equals(status)) throw BizException.of(400, "请求参数错误");
        Long id = parseId(idStr);
        Spu s = id == null ? null : spuMapper.selectById(id);
        if (s == null) throw BizException.of(2001, "商品不存在");
        Spu u = new Spu();
        u.setId(id);
        u.setStatus(status);
        spuMapper.updateById(u);
        cache.delayDoubleDelete(id);
    }

    /** 先写 DB（含冗余 min_price）→ 提交后 DEL 缓存 → 500ms 后再 DEL */
    @Transactional(rollbackFor = Exception.class)
    public void updateSku(String idStr, Integer price, Integer stock) {
        Long id = parseId(idStr);
        Sku k = id == null ? null : skuMapper.selectById(id);
        if (k == null) throw BizException.of(2001, "SKU 不存在");
        if ((price != null && price <= 0) || (stock != null && stock < 0)) throw BizException.of(400, "请求参数错误");
        Sku u = new Sku();
        u.setId(id);
        u.setPrice(price);
        u.setStock(stock);
        if (price != null || stock != null) skuMapper.updateById(u);
        int min = skuMapper.selectList(new LambdaQueryWrapper<Sku>().select(Sku::getPrice).eq(Sku::getSpuId, k.getSpuId()))
                .stream().mapToInt(Sku::getPrice).min().orElse(k.getPrice());
        Spu s = new Spu();
        s.setId(k.getSpuId());
        s.setMinPrice(min);
        spuMapper.updateById(s);
        cache.delayDoubleDelete(k.getSpuId());
    }
}
