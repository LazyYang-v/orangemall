package com.orangemall.seckill.service;

import com.orangemall.seckill.entity.SeckillOrder;
import com.orangemall.seckill.mapper.SeckillActivityMapper;
import com.orangemall.seckill.mapper.SeckillOrderMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 秒杀落单的「记成交 + 扣 DB 库存」合并器（OM-025，group commit）。
 *
 * <p>问题：每单一次 {@code UPDATE seckill_activity SET stock = stock - 1}，所有消费线程争同一热点行，
 * 行锁要等提交刷盘才释放；宿主机 CPU 紧张时持锁线程被调度出去，其余线程在锁上排队（线程快照 12 个消费线程 7 个卡在这里），
 * 落单吞吐被压在 ~150 单/s。且原实现「扣库存」「写 seckill_order」是两次独立的自动提交，写入失败重试会重复扣减。
 *
 * <p>做法：消费线程提交请求后阻塞等待；单个刷写线程把排队的请求按活动合并，一个本地事务内
 * {@code INSERT IGNORE} 批量写成交（uk_activity_user 去重，返回实际新插入数 k）→ {@code stock = stock - k WHERE stock >= k}。
 * 热点行每批只更新一次；成交记录与扣减同事务，要么都生效要么都不生效。
 * 整批库存不足时回滚，退回逐条事务（与原来的逐单乐观锁语义一致：先到先得，扣不到的返回 false）。
 *
 * <p>返回值：true = 已记成交（含该用户此前已成交的幂等情形），false = DB 库存兜底拦截。
 */
@Slf4j
@Component
public class SeckillDeductBatcher {
    private static final int MAX_BATCH = 200;
    private static final long WAIT_SECONDS = 10;

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper orderMapper;
    private final TransactionTemplate tx;
    private final LinkedBlockingQueue<Req> queue = new LinkedBlockingQueue<>();
    private final Thread flusher;
    private volatile boolean running = true;

    private record Req(String activityId, long userId, long orderId, CompletableFuture<Boolean> done) {}

    public SeckillDeductBatcher(SeckillActivityMapper activityMapper, SeckillOrderMapper orderMapper, TransactionTemplate tx) {
        this.activityMapper = activityMapper;
        this.orderMapper = orderMapper;
        this.tx = tx;
        this.flusher = new Thread(this::loop, "seckill-deduct-batcher");
        this.flusher.setDaemon(true);
        this.flusher.start();
    }

    /** 阻塞直到本请求所在批次提交；超时/异常抛出，由消费者按原重试逻辑处理（uk 保证重试幂等，不会重复扣减） */
    public boolean deduct(String activityId, long userId, long orderId) {
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        queue.add(new Req(activityId, userId, orderId, f));
        try {
            return f.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting deduct", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            throw c instanceof RuntimeException re ? re : new IllegalStateException(c);
        } catch (TimeoutException e) {
            throw new IllegalStateException("deduct batch timeout " + WAIT_SECONDS + "s", e);
        }
    }

    private void loop() {
        List<Req> batch = new ArrayList<>(MAX_BATCH);
        while (running) {
            try {
                Req first = queue.poll(500, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                batch.add(first);
                queue.drainTo(batch, MAX_BATCH - 1);
                Map<String, List<Req>> byActivity = new LinkedHashMap<>();
                for (Req r : batch) byActivity.computeIfAbsent(r.activityId(), k -> new ArrayList<>()).add(r);
                byActivity.forEach(this::flush);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                // flush 内部已逐个完成 future；这里兜底，避免刷写线程退出
                log.error("deduct batcher loop error", t);
                batch.forEach(r -> r.done().completeExceptionally(t));
            } finally {
                batch.clear();
            }
        }
    }

    private void flush(String activityId, List<Req> group) {
        try {
            Boolean ok = tx.execute(s -> {
                int inserted = orderMapper.insertIgnore(activityId, rows(group));
                if (inserted == 0) return true; // 全部是重复（重试幂等），无需扣减
                if (activityMapper.decrStockBy(activityId, inserted) == 1) return true;
                s.setRollbackOnly(); // 整批库存不足：回滚，退回逐条
                return false;
            });
            if (Boolean.TRUE.equals(ok)) {
                group.forEach(r -> r.done().complete(true));
                return;
            }
            for (Req r : group) r.done().complete(single(r));
        } catch (RuntimeException e) {
            log.warn("deduct batch {} x{} failed: {}", activityId, group.size(), e.toString());
            group.forEach(r -> r.done().completeExceptionally(e));
        }
    }

    /** 逐条：与原逐单乐观锁一致；单条失败只影响该请求 */
    private boolean single(Req r) {
        try {
            return Boolean.TRUE.equals(tx.execute(s -> {
                if (orderMapper.insertIgnore(r.activityId(), rows(List.of(r))) == 0) return true;
                if (activityMapper.decrStock(r.activityId()) == 1) return true;
                s.setRollbackOnly();
                return false;
            }));
        } catch (RuntimeException e) {
            r.done().completeExceptionally(e);
            return false; // future 已异常完成，complete(false) 不再生效
        }
    }

    private static List<SeckillOrder> rows(List<Req> group) {
        List<SeckillOrder> rows = new ArrayList<>(group.size());
        for (Req r : group) {
            SeckillOrder o = new SeckillOrder();
            o.setActivityId(r.activityId());
            o.setUserId(r.userId());
            o.setOrderId(r.orderId());
            rows.add(o);
        }
        return rows;
    }

    @PreDestroy
    void stop() {
        running = false;
        flusher.interrupt();
        Req r;
        while ((r = queue.poll()) != null) r.done().completeExceptionally(new IllegalStateException("shutting down"));
    }
}
