package com.orangemall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orangemall.api.dto.SkuInfo;
import com.orangemall.api.dto.StockItem;
import com.orangemall.api.dto.StockOpReq;
import com.orangemall.common.BizException;
import com.orangemall.product.entity.Brand;
import com.orangemall.product.entity.Sku;
import com.orangemall.product.entity.Spu;
import com.orangemall.product.mapper.SkuMapper;
import com.orangemall.product.mapper.StockLedgerMapper;
import com.orangemall.product.mapper.SpuMapper;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内部接口：SKU 快照、库存扣减 / 回补（Seata AT 分支，XID 由 seata starter 从请求头 TX_XID 绑定）。
 * 普通订单的扣减 / 回补都按订单记 stock_ledger 流水（V3），流水唯一键让两者各自幂等：
 * 补偿重复到达（MQ 重投、DLQ 重驱动、超时扫描、退款回调重放）在库存层也只生效一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
    static final String DEDUCT = "DEDUCT";
    static final String RESTORE = "RESTORE";

    private final SkuMapper skuMapper;
    private final StockLedgerMapper ledger;
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
     * 先记 DEDUCT 流水，再 UPDATE sku SET stock=stock-n, sales=sales+n WHERE id=? AND stock>=n。
     * 流水已存在（同一订单重复调用）⇒ 视为已扣，直接返回；任一 SKU 影响 0 行 ⇒ 抛 3001，本地事务（含流水）回滚，
     * 处于全局事务中时异常回传给 TM 触发全局回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deduct(StockOpReq req) {
        long orderId = orderId(req);
        TreeMap<Long, Integer> m = normalize(req.items());
        Set<Long> spuIds = new HashSet<>();
        for (Map.Entry<Long, Integer> e : m.entrySet()) {
            try {
                ledger.insert(orderId, e.getKey(), DEDUCT, e.getValue());
            } catch (DuplicateKeyException dup) {
                log.info("stock already deducted for order {} sku {}, skip", orderId, e.getKey());
                continue;
            }
            if (skuMapper.deduct(e.getKey(), e.getValue()) == 0) {
                Sku k = skuMapper.selectById(e.getKey());
                if (k == null) throw BizException.of(2001, "商品不存在");
                Spu s = spuMapper.selectById(k.getSpuId());
                throw BizException.of(3001, s == null ? "库存不足" : "「" + s.getName() + "」库存不足");
            }
            spuIds.add(skuSpu(e.getKey()));
        }
        if (RootContext.inGlobalTransaction()) log.info("stock deducted in global tx {} order {} : {}", RootContext.getXID(), orderId, m);
        cache.evictQuietly(spuIds);
        return true;
    }

    /**
     * 按订单回补：每个 SKU 先插 RESTORE 流水（唯一键 ⇒ 第二次起 DuplicateKey，跳过），再加回库存。
     * 件数取该订单的 DEDUCT 流水；订单没有任何 DEDUCT 流水 ⇒ 视为 V3 之前创建的历史订单，按请求件数回补（仍受 RESTORE 唯一键保护）；
     * 订单有流水但某 SKU 没扣过 ⇒ 不回补该 SKU。返回实际回补的 SKU 行数。
     * <p>{@code @GlobalLock}（Phase 5）：关单 / 退款的回补不在全局事务里，但同一 SKU 行可能正被某个 order-submit 的 AT 分支持有全局锁；
     * 不等锁直接改行，那个全局事务一旦回滚，undo 校验发现行已被改动（dirty undo log）→ TimeoutRollbackFailed，全局锁永不释放、该 SKU 再也下不了单
     * （TC 宕机演练中实际发生过）。改行前先 SELECT … FOR UPDATE：全局锁被占则放掉行锁、每 50ms 重试、最多 4s（调用方 Feign 读超时 5s；调用方会整体重试，流水保证幂等）。
     */
    @GlobalLock(lockRetryInterval = 50, lockRetryTimes = 80)
    @Transactional(rollbackFor = Exception.class)
    public int restore(StockOpReq req) {
        long orderId = orderId(req);
        TreeMap<Long, Integer> m = normalize(req.items());
        Map<Long, Integer> deducted = new HashMap<>();
        for (Map<String, Object> row : ledger.byOrder(orderId)) {
            if (DEDUCT.equals(row.get("op"))) deducted.put(((Number) row.get("skuId")).longValue(), ((Number) row.get("qty")).intValue());
        }
        boolean legacy = deducted.isEmpty();
        Set<Long> spuIds = new HashSet<>();
        int restored = 0;
        for (Map.Entry<Long, Integer> e : m.entrySet()) {
            int qty = legacy ? e.getValue() : deducted.getOrDefault(e.getKey(), 0);
            if (qty <= 0) {
                log.warn("order {} never deducted sku {}, skip restore", orderId, e.getKey());
                continue;
            }
            if (!legacy && qty != e.getValue()) log.warn("order {} sku {}: restore {} by ledger (caller asked {})", orderId, e.getKey(), qty, e.getValue());
            skuMapper.lockById(e.getKey()); // 先等全局锁再拿行锁（见 SkuMapper#lockById）；TreeMap 有序，多 SKU 加锁顺序固定
            try {
                ledger.insert(orderId, e.getKey(), RESTORE, qty);
            } catch (DuplicateKeyException dup) {
                log.info("stock already restored for order {} sku {}, skip", orderId, e.getKey());
                continue;
            }
            if (skuMapper.restore(e.getKey(), qty) > 0) {
                spuIds.add(skuSpu(e.getKey()));
                restored++;
            }
        }
        cache.evictQuietly(spuIds);
        return restored;
    }

    private static long orderId(StockOpReq req) {
        if (req == null || req.orderId() == null || req.orderId() <= 0) throw BizException.of(400, "请求参数错误");
        return req.orderId();
    }

    private Long skuSpu(Long skuId) {
        Sku k = skuMapper.selectOne(new LambdaQueryWrapper<Sku>().select(Sku::getSpuId).eq(Sku::getId, skuId));
        return k == null ? null : k.getSpuId();
    }
}
