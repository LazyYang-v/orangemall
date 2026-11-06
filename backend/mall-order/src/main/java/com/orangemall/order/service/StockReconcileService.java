package com.orangemall.order.service;

import com.orangemall.api.client.ProductInnerClient;
import com.orangemall.api.dto.LedgerEntry;
import com.orangemall.api.dto.StockItem;
import com.orangemall.api.dto.StockOpReq;
import com.orangemall.order.domain.Shard;
import com.orangemall.order.mapper.OrderMapper;
import com.orangemall.web.OpsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存对账（Phase 6）：以 mall_product.stock_ledger 为准核对普通订单与库存。
 * <ol>
 *   <li><b>已关闭 / 已取消但没回补</b>：窗口内关闭的普通单，凡有 DEDUCT 而缺 RESTORE 的 SKU，直接调用回补（按订单幂等，
 *       重复执行也只补一次）；回补失败转入关单回补后台重试。这一项兜住所有「回补丢了」的路径：
 *       同步 + 后台重试都耗尽、后台重试领取后实例崩溃、Redis 丢数据。</li>
 *   <li><b>普通单没有扣减流水</b>：窗口内创建的普通单在流水里找不到 DEDUCT ⇒ 这单没占库存（可能超卖），只告警。</li>
 *   <li><b>扣减流水没有订单</b>：窗口内的 DEDUCT 找不到订单 ⇒ 库存被占却无单可关（库存少了），只告警，
 *       因为这类数据只可能来自缺陷或人工改库，自动回补可能掩盖问题。</li>
 * </ol>
 * 窗口的右端必须早于「下单全局事务超时 + 关单回补后台重试」，否则会把正常在途的单当成异常（见 {@link #SETTLE}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockReconcileService {
    /** 窗口右端距现在至少这么久：order-submit 全局超时 10s、关单回补后台重试约 2 分钟，留足余量 */
    public static final Duration SETTLE = Duration.ofMinutes(5);
    /** 订单与流水各自用 DB 时钟写 created_at，两者相差不超过一个下单全局事务（10s）；比对「订单有没有流水」时流水窗口两侧放宽 */
    static final Duration SKEW = Duration.ofSeconds(60);
    static final int PER_SHARD_LIMIT = 10_000;
    static final int LEDGER_LIMIT = 20_000;
    static final int BATCH = 500;
    static final String DEDUCT = "DEDUCT", RESTORE = "RESTORE";

    private final OrderMapper orderMapper;
    private final ProductInnerClient product;
    private final OrderService orders;
    private final OpsLogger ops;

    public record Report(Instant from, Instant to, int closedChecked, List<Long> restored, List<Long> deferred,
                         int createdChecked, List<Long> orderWithoutDeduct, List<Long> deductWithoutOrder, boolean truncated) {
        public boolean clean() {
            return restored.isEmpty() && deferred.isEmpty() && orderWithoutDeduct.isEmpty() && deductWithoutOrder.isEmpty();
        }
    }

    public Report reconcile(Instant from, Instant to) {
        boolean[] truncated = {false};
        List<Long> restored = new ArrayList<>(), deferred = new ArrayList<>();

        // ① 已关闭 / 已取消的普通单：缺 RESTORE 的直接补
        List<Long> closed = idsAcrossShards((s, lim) -> orderMapper.closedNormalBetween(s, from, to, lim), truncated);
        for (List<Long> batch : batches(closed)) {
            Map<Long, List<LedgerEntry>> byOrder = product.ledgerByOrders(batch).unwrap().stream()
                    .collect(Collectors.groupingBy(LedgerEntry::orderId));
            for (Long id : batch) {
                List<StockItem> missing = missingRestore(byOrder.getOrDefault(id, List.of()));
                if (missing.isEmpty()) continue;
                try {
                    product.restore(new StockOpReq(id, missing)).unwrap();
                    restored.add(id);
                    ops.log("ORDER", "[库存对账] 订单 " + id + " 已关闭但 " + missing.size() + " 个 SKU 未回补库存，已自动补偿");
                } catch (Exception e) {
                    deferred.add(id);
                    orders.deferRestore(id, "库存对账发现未回补、补偿失败", e.getMessage());
                }
            }
        }

        // ② 普通单没有扣减流水
        List<Long> created = idsAcrossShards((s, lim) -> orderMapper.createdNormalBetween(s, from, to, lim), truncated);
        Set<Long> deductedWide = new HashSet<>(deducted(from.minus(SKEW), to.plus(SKEW), truncated));
        List<Long> orderWithoutDeduct = created.stream().filter(id -> !deductedWide.contains(id)).sorted().toList();

        // ③ 扣减流水没有订单（窗口内的流水逐单查订单；查不到再看归档表）
        Set<Long> createdSet = new HashSet<>(created);
        List<Long> deductWithoutOrder = deducted(from, to, truncated).stream()
                .filter(id -> !createdSet.contains(id))
                .filter(id -> orderMapper.findOrder(Shard.of(id), id) == null && orderMapper.findOrder(Shard.ARCHIVE, id) == null)
                .sorted().toList();

        Report r = new Report(from, to, closed.size(), restored, deferred, created.size(), orderWithoutDeduct, deductWithoutOrder, truncated[0]);
        if (!orderWithoutDeduct.isEmpty())
            ops.log("ALERT", "[库存对账] " + orderWithoutDeduct.size() + " 个普通单没有扣减流水（未占库存，可能超卖）：" + head(orderWithoutDeduct));
        if (!deductWithoutOrder.isEmpty())
            ops.log("ALERT", "[库存对账] " + deductWithoutOrder.size() + " 条扣减流水找不到订单（库存被占用）：" + head(deductWithoutOrder));
        if (truncated[0])
            ops.log("ALERT", "[库存对账] 窗口 " + from + " ~ " + to + " 数据量超过单次上限，部分未核对");
        log.info("stock reconcile {} ~ {}: closed {} restored {} deferred {} created {} noDeduct {} orphanDeduct {} truncated {}",
                from, to, closed.size(), restored.size(), deferred.size(), created.size(), orderWithoutDeduct.size(), deductWithoutOrder.size(), truncated[0]);
        return r;
    }

    /** 某订单流水里「扣过但没回补」的 SKU 与件数 */
    static List<StockItem> missingRestore(List<LedgerEntry> rows) {
        Set<Long> restoredSkus = rows.stream().filter(e -> RESTORE.equals(e.op())).map(LedgerEntry::skuId).collect(Collectors.toSet());
        return rows.stream().filter(e -> DEDUCT.equals(e.op()) && !restoredSkus.contains(e.skuId()))
                .sorted(Comparator.comparing(LedgerEntry::skuId))
                .map(e -> new StockItem(e.skuId(), e.qty())).toList();
    }

    private interface ShardQuery {
        List<Long> run(String shard, int limit);
    }

    private static List<Long> idsAcrossShards(ShardQuery q, boolean[] truncated) {
        List<Long> out = new ArrayList<>();
        for (String s : Shard.SHARDS) {
            List<Long> ids = q.run(s, PER_SHARD_LIMIT);
            if (ids.size() >= PER_SHARD_LIMIT) truncated[0] = true;
            out.addAll(ids);
        }
        return out;
    }

    private List<Long> deducted(Instant from, Instant to, boolean[] truncated) {
        List<Long> ids = product.deducted(from.toEpochMilli(), to.toEpochMilli(), LEDGER_LIMIT).unwrap();
        if (ids.size() >= LEDGER_LIMIT) truncated[0] = true;
        return ids;
    }

    private static List<List<Long>> batches(List<Long> ids) {
        List<List<Long>> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += BATCH) out.add(ids.subList(i, Math.min(ids.size(), i + BATCH)));
        return out;
    }

    private static String head(List<Long> ids) {
        return ids.stream().limit(20).map(String::valueOf).collect(Collectors.joining(", ")) + (ids.size() > 20 ? " …" : "");
    }
}
