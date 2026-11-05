package com.orangemall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orangemall.api.dto.SkuInfo;
import com.orangemall.api.dto.StockItem;
import com.orangemall.common.BizException;
import com.orangemall.product.entity.Brand;
import com.orangemall.product.entity.Sku;
import com.orangemall.product.entity.Spu;
import com.orangemall.product.mapper.SkuMapper;
import com.orangemall.product.mapper.SpuMapper;
import io.seata.core.context.RootContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 内部接口：SKU 快照、库存扣减 / 回补（Seata AT 分支，XID 由 seata starter 从请求头 TX_XID 绑定） */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final ProductCacheService cache;

    public List<SkuInfo> skus(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) return List.of();
        List<Long> ids = skuIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, Sku> skus = skuMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Sku::getId, Function.identity()));
        if (skus.isEmpty()) return List.of();
        Map<Long, Spu> spus = spuMapper.selectList(new LambdaQueryWrapper<Spu>()
                        .select(Spu::getId, Spu::getName, Spu::getArt, Spu::getTint, Spu::getStatus, Spu::getShopName, Spu::getCategoryId, Spu::getBrandId)
                        .in(Spu::getId, skus.values().stream().map(Sku::getSpuId).distinct().toList()))
                .stream().collect(Collectors.toMap(Spu::getId, Function.identity()));
        Map<Integer, Brand> brands = productService.brands();
        List<SkuInfo> out = new ArrayList<>();
        for (Long id : ids) {
            Sku k = skus.get(id);
            Spu s = k == null ? null : spus.get(k.getSpuId());
            if (s == null) continue;
            String shop = s.getShopName() != null ? s.getShopName()
                    : Optional.ofNullable(brands.get(s.getBrandId())).map(Brand::getShopName).orElse("");
            out.add(new SkuInfo(k.getId(), s.getId(), s.getName(), ProductService.specText(productService.specs(k.getSpecs())),
                    s.getArt(), s.getTint(), k.getPrice(), k.getOriginalPrice(), k.getStock(), s.getStatus(), shop,
                    s.getCategoryId(), categoryService.rootOf(s.getCategoryId())));
        }
        return out;
    }

    /** 合并同 SKU、按 skuId 升序加锁，避免多 SKU 订单交叉加锁死锁 */
    private static TreeMap<Long, Integer> normalize(List<StockItem> items) {
        if (items == null || items.isEmpty()) throw BizException.of(400, "请求参数错误");
        TreeMap<Long, Integer> m = new TreeMap<>();
        for (StockItem it : items) {
            if (it == null || it.skuId() == null || it.count() <= 0) throw BizException.of(400, "请求参数错误");
            m.merge(it.skuId(), it.count(), Integer::sum);
        }
        return m;
    }

    /**
     * UPDATE sku SET stock=stock-n, sales=sales+n WHERE id=? AND stock>=n。
     * 任一 SKU 影响 0 行 ⇒ 抛 3001，本地事务回滚；处于全局事务中时异常回传给 TM 触发全局回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deduct(List<StockItem> items) {
        TreeMap<Long, Integer> m = normalize(items);
        Set<Long> spuIds = new HashSet<>();
        for (Map.Entry<Long, Integer> e : m.entrySet()) {
            if (skuMapper.deduct(e.getKey(), e.getValue()) == 0) {
                Sku k = skuMapper.selectById(e.getKey());
                if (k == null) throw BizException.of(2001, "商品不存在");
                Spu s = spuMapper.selectById(k.getSpuId());
                throw BizException.of(3001, s == null ? "库存不足" : "「" + s.getName() + "」库存不足");
            }
            spuIds.add(skuSpu(e.getKey()));
        }
        if (RootContext.inGlobalTransaction()) log.info("stock deducted in global tx {} : {}", RootContext.getXID(), m);
        cache.evictQuietly(spuIds);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean restore(List<StockItem> items) {
        TreeMap<Long, Integer> m = normalize(items);
        Set<Long> spuIds = new HashSet<>();
        for (Map.Entry<Long, Integer> e : m.entrySet()) {
            if (skuMapper.restore(e.getKey(), e.getValue()) > 0) spuIds.add(skuSpu(e.getKey()));
        }
        cache.evictQuietly(spuIds);
        return true;
    }

    private Long skuSpu(Long skuId) {
        Sku k = skuMapper.selectOne(new LambdaQueryWrapper<Sku>().select(Sku::getSpuId).eq(Sku::getId, skuId));
        return k == null ? null : k.getSpuId();
    }
}
