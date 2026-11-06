package com.orangemall.order.job;

import com.orangemall.order.service.StockReconcileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 检查点推进：首轮只落检查点；之后每轮最多 30 分钟、右端 = now − 5min；对账失败检查点不动 */
class StockReconcileJobTest {
    private final StockReconcileService reconciler = mock(StockReconcileService.class);
    private final Map<String, String> kv = new HashMap<>();
    private StockReconcileJob job;
    private final Instant t0 = Instant.parse("2026-11-06T10:00:00Z");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> v = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(v);
        when(v.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> kv.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        when(v.get(anyString())).thenAnswer(inv -> kv.get((String) inv.getArgument(0)));
        doAnswer(inv -> kv.put(inv.getArgument(0), inv.getArgument(1))).when(v).set(anyString(), anyString());
        when(redis.delete(anyString())).thenAnswer(inv -> kv.remove((String) inv.getArgument(0)) != null);
        job = new StockReconcileJob(reconciler, redis);
    }

    private Instant ckpt() {
        return Instant.ofEpochMilli(Long.parseLong(kv.get(StockReconcileJob.CKPT)));
    }

    @Test
    void firstRunOnlySetsCheckpointThenWindowsTrailBySettle() {
        job.run(t0);
        verifyNoInteractions(reconciler);
        assertThat(ckpt()).isEqualTo(t0);

        job.run(t0.plusSeconds(240)); // 右端 t0−1min 还在检查点之前：不跑
        verifyNoInteractions(reconciler);

        job.run(t0.plusSeconds(360));
        verify(reconciler).reconcile(t0, t0.plusSeconds(60));
        assertThat(ckpt()).isEqualTo(t0.plusSeconds(60));
    }

    @Test
    void laggingCheckpointCatchesUpInThirtyMinuteSteps() {
        kv.put(StockReconcileJob.CKPT, String.valueOf(t0.toEpochMilli()));
        Instant now = t0.plus(Duration.ofHours(2));
        job.run(now);
        job.run(now);
        verify(reconciler).reconcile(t0, t0.plus(Duration.ofMinutes(30)));
        verify(reconciler).reconcile(t0.plus(Duration.ofMinutes(30)), t0.plus(Duration.ofMinutes(60)));
        assertThat(ckpt()).isEqualTo(t0.plus(Duration.ofMinutes(60)));
        assertThat(kv).doesNotContainKey(StockReconcileJob.CKPT + ":lock");
    }

    @Test
    void failedRoundKeepsCheckpointAndReleasesLock() {
        kv.put(StockReconcileJob.CKPT, String.valueOf(t0.toEpochMilli()));
        when(reconciler.reconcile(any(), any())).thenThrow(new RuntimeException("mall-product 不可用"));
        job.run(t0.plus(Duration.ofHours(1)));
        assertThat(ckpt()).isEqualTo(t0);
        assertThat(kv).doesNotContainKey(StockReconcileJob.CKPT + ":lock");
    }

    @Test
    void anotherInstanceHoldingTheLockSkipsTheRound() {
        kv.put(StockReconcileJob.CKPT, String.valueOf(t0.toEpochMilli()));
        kv.put(StockReconcileJob.CKPT + ":lock", "1");
        job.run(t0.plus(Duration.ofHours(1)));
        verifyNoInteractions(reconciler);
        assertThat(kv).containsKey(StockReconcileJob.CKPT + ":lock");
    }
}
