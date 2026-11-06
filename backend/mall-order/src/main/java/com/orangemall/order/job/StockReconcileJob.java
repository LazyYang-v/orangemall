package com.orangemall.order.job;

import com.orangemall.order.service.StockReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 库存对账定时任务（Phase 6，见 {@link StockReconcileService}）：每分钟一轮，Redis SETNX 多实例互斥。
 * 检查点（epoch 毫秒）存 Redis，每轮最多推进 30 分钟，窗口右端 = now − SETTLE；检查点落后时逐轮追上。
 * 首次运行把检查点设为当前时刻（只核对之后的数据）：之前的订单可能早于库存流水上线，没有流水不代表异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockReconcileJob {
    static final String CKPT = "order:stock-reconcile:since";
    static final Duration MAX_STEP = Duration.ofMinutes(30);
    static final Duration MAX_LAG = Duration.ofHours(24);

    private final StockReconcileService reconciler;
    private final StringRedisTemplate redis;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void run() {
        run(Instant.now());
    }

    void run(Instant now) {
        Boolean got = redis.opsForValue().setIfAbsent(CKPT + ":lock", "1", Duration.ofSeconds(240));
        if (!Boolean.TRUE.equals(got)) return;
        try {
            String raw = redis.opsForValue().get(CKPT);
            if (raw == null) {
                redis.opsForValue().set(CKPT, String.valueOf(now.toEpochMilli()));
                return;
            }
            Instant end = now.minus(StockReconcileService.SETTLE);
            Instant from = Instant.ofEpochMilli(Long.parseLong(raw));
            if (from.isBefore(now.minus(MAX_LAG))) from = now.minus(MAX_LAG); // 停机很久：只追最近 24h（更早的用后台接口按需对账）
            if (!from.isBefore(end)) return;
            Instant to = from.plus(MAX_STEP).isBefore(end) ? from.plus(MAX_STEP) : end;
            reconciler.reconcile(from, to);
            redis.opsForValue().set(CKPT, String.valueOf(to.toEpochMilli()));
        } catch (Exception e) {
            log.warn("stock reconcile failed (checkpoint unchanged, retry next round): {}", e.getMessage());
        } finally {
            redis.delete(CKPT + ":lock");
        }
    }
}
