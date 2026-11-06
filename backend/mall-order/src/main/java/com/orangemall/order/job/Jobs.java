package com.orangemall.order.job;

import com.orangemall.common.Const;
import com.orangemall.order.domain.Logistics;
import com.orangemall.order.domain.OrderRow;
import com.orangemall.order.domain.OrderStatus;
import com.orangemall.order.mapper.OrderMapper;
import com.orangemall.order.service.OrderAssembler;
import com.orangemall.order.service.OrderService;
import com.orangemall.web.Notifier;
import com.orangemall.web.OpsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 兜底与仿真任务：
 * 1. 超时扫描：MQ 丢失时兜底关单（每 60s）；
 * 2. 物流模拟器：替代快递鸟轨迹订阅推送，已发货订单每 12s 推进一步（MQ → WebSocket）；
 * 3. 归档：每天 03:30 把 N 天前已完结订单迁入 t_order_archive。
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class Jobs {
    private static final long LOGISTICS_STEP_MS = 12_000;

    private final OrderMapper orderMapper;
    private final OrderService orders;
    private final OrderAssembler assembler;
    private final Notifier notifier;
    private final OpsLogger ops;
    private final RabbitTemplate rabbit;

    /** 超时兜底扫描（正常由 RabbitMQ TTL+DLX 触发） */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void sweepOverdue() {
        for (String s : ShardShim.SHARDS) {
            List<OrderRow> overdue = orderMapper.overduePending(s, Instant.now(), 100);
            for (OrderRow o : overdue) {
                orders.close(o, "支付超时，订单自动关闭", OrderStatus.CLOSED);
            }
        }
    }

    /** 关单回补后台重试（同步重试失败的订单登记在 Redis 延迟集合，见 OrderService#restoreStock） */
    @Scheduled(fixedDelay = 5_000, initialDelay = 15_000)
    public void retryRestores() {
        try {
            orders.retryPendingRestores();
        } catch (Exception e) {
            log.warn("restore retry sweep failed: {}", e.getMessage());
        }
    }

    /** 物流轨迹模拟器：替代快递鸟订阅推送 */
    @Scheduled(fixedDelay = 5_000, initialDelay = 20_000)
    public void advanceLogistics() {
        for (String s : ShardShim.SHARDS) {
            for (OrderRow o : orderMapper.shippedInTransit(s, 200)) {
                try {
                    Logistics.Info lg = assembler.logistics(o.getLogisticsJson());
                    if (lg == null || lg.traces() == null || lg.traces().isEmpty()) continue;
                    // 最新轨迹已是最后一步（已签收）则停止追加：模拟器只替代"配送中"的订阅推送，
                    // 终态交由用户确认收货，避免每 12s 重复 prepend 导致 logistics_json 无界增长
                    if (Logistics.STEPS.get(Logistics.STEPS.size() - 1).state().equals(lg.traces().get(0).state())) continue;
                    Logistics.Trace step = Logistics.STEPS.get(Math.min(lg.traces().size() - 1, Logistics.STEPS.size() - 1));
                    if (step == null) continue;
                    Instant last = Instant.parse(lg.traces().get(0).time());
                    if (Duration.between(last, Instant.now()).toMillis() < LOGISTICS_STEP_MS) continue;
                    Logistics.Trace t = new Logistics.Trace(Logistics.iso(Instant.now()), step.state(), step.desc(), step.location());
                    Logistics.Info next = lg.prepend(t);
                    if (orderMapper.casLogistics(s, o.getId(), assembler.write(lg), assembler.write(next)) > 0) {
                        ops.log("MQ", "[物流回调] 快递鸟推送 " + lg.trackingNo() + " " + t.desc() + " → MQ → WebSocket");
                        Map<String, Object> trace = Map.of("time", t.time(), "state", t.state(), "desc", t.desc(), "location", t.location());
                        notifier.send(o.getUserId(), Map.of("type", "LOGISTICS_UPDATE", "orderId", String.valueOf(o.getId()), "trace", trace));
                    }
                } catch (Exception e) {
                    log.warn("订单 {} 物流推进失败，跳过: {}", o.getId(), e.getMessage());
                }
            }
        }
    }

    /** 归档：03:30 每日执行 */
    @Scheduled(cron = "${mall.order.archive-cron:0 30 3 * * *}", zone = "Asia/Shanghai")
    public void archive() {
        Instant before = Instant.now().minus(Duration.ofDays(90));
        for (String s : ShardShim.SHARDS) {
            while (true) {
                List<Long> ids = orderMapper.archivableIds(s, before, 500);
                if (ids.isEmpty()) break;
                orderMapper.copyToArchive(s, ids);
                orderMapper.copyItemsToArchive(s, ids);
                orderMapper.deleteItems(s, ids);
                orderMapper.deleteOrders(s, ids);
                ops.log("ARCHIVE", "[归档] " + ids.size() + " 条订单迁入 t_order_archive");
                if (ids.size() < 500) break;
            }
        }
    }

    /** 避免循环依赖的静态常量（与 domain.Shard 一致） */
    static final class ShardShim {
        static final List<String> SHARDS = List.of("0", "1", "2", "3");
        private ShardShim() {
        }
    }
}
