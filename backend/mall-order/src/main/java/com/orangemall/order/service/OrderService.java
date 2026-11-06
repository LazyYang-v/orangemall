package com.orangemall.order.service;

import com.orangemall.api.client.MarketingInnerClient;
import com.orangemall.api.client.ProductInnerClient;
import com.orangemall.api.client.UserInnerClient;
import com.orangemall.api.dto.*;
import com.orangemall.common.BizException;
import com.orangemall.common.Const;
import com.orangemall.common.IdGenerator;
import com.orangemall.common.PageResult;
import com.orangemall.order.domain.*;
import com.orangemall.order.mapper.OrderMapper;
import com.orangemall.web.OpsLogger;
import com.orangemall.web.Notifier;
import com.orangemall.web.UserContext;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单核心：预览计价 → 幂等提交（Seata AT 全局事务）→ MQ 延迟关单 → 状态机。
 * 查询按 userId&3 路由单分表，后台/看板跨全部分表 + 归档表。
 */
@Service
@RequiredArgsConstructor
public class OrderService {
    private static final long ORDER_TTL_MINUTES = 30;
    private static final long SECKILL_TTL_MINUTES = 10;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /**
     * 秒杀落单的 SKU 快照本地缓存（OM-025）：同一场活动的上千单都是同一个 SKU，每单 Feign 一次商品服务纯属浪费。
     * 只缓存展示/原价字段（名称、规格、原价），成交价来自秒杀消息本身；10s 过期，后台改价最多延迟 10s 生效。
     */
    private static final long SKU_TTL_MS = 10_000;
    private record CachedSku(SkuInfo sku, long at) {}
    private final java.util.concurrent.ConcurrentHashMap<Long, CachedSku> seckillSkus = new java.util.concurrent.ConcurrentHashMap<>();

    private final OrderMapper orderMapper;
    private final OrderAssembler assembler;
    private final ProductInnerClient product;
    private final UserInnerClient userClient;
    private final MarketingInnerClient marketing;
    private final RabbitTemplate rabbit;
    private final StringRedisTemplate redis;
    private final com.fasterxml.jackson.databind.ObjectMapper om;
    private final OpsLogger ops;
    private final Notifier notifier;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    /* ============================ 计价行 ============================ */
    private record Line(SkuInfo sku, int count) {
    }

    private List<Line> buildLines(List<Map<String, Object>> rawItems) {
        List<Long> ids = new ArrayList<>();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> it : rawItems) {
            long skuId = Long.parseLong(String.valueOf(it.get("skuId")));
            ids.add(skuId);
            counts.merge(skuId, ((Number) it.getOrDefault("count", 1)).intValue(), Integer::sum);
        }
        Map<Long, SkuInfo> skus = new HashMap<>();
        for (SkuInfo s : product.skus(ids).unwrap()) skus.put(s.skuId(), s);
        List<Line> lines = new ArrayList<>();
        // 按去重后的 SKU 建行：同一 SKU 在请求里出现多次时合并为一行（修复前会生成多行、每行都是合并后的件数 → 重复计价与扣减）
        for (long id : counts.keySet()) {
            SkuInfo s = skus.get(id);
            if (s == null) throw new BizException(2001, "商品不存在");
            lines.add(new Line(s, counts.get(id)));
        }
        return lines;
    }

    private Pricing.Calc calc(List<Line> lines, UserCouponDTO coupon, int pointsUsed, int balance) {
        return Pricing.calc(lines.stream().map(l -> new Pricing.Line(l.sku(), l.count())).toList(), coupon, pointsUsed, balance);
    }

    private static Map<String, Object> couponMap(UserCouponDTO c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id() == null ? null : String.valueOf(c.id()));
        m.put("templateId", c.templateId() == null ? null : String.valueOf(c.templateId()));
        m.put("title", c.title());
        m.put("type", c.type());
        m.put("threshold", c.threshold());
        m.put("amount", c.amount());
        m.put("scope", c.scope());
        m.put("scopeRootId", c.scopeRootId());
        m.put("status", c.status());
        m.put("validFrom", c.validFrom());
        m.put("validTo", c.validTo());
        return m;
    }

    /* ============================ 预览 ============================ */
    public Map<String, Object> preview(long userId, Map<String, Object> body) {
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        List<Line> lines = buildLines(rawItems);
        List<UserCouponDTO> coupons = marketing.userCoupons(userId).unwrap().stream()
                .filter(c -> "UNUSED".equals(c.status()) && c.validTo() != null && c.validTo().isAfter(Instant.now()))
                .toList();
        List<Map<String, Object>> available = new ArrayList<>();
        List<Map<String, Object>> unavailable = new ArrayList<>();
        for (UserCouponDTO c : coupons) {
            int eligible = Pricing.eligibleAmount(toPricing(lines), c);
            if (eligible <= 0) unavailable.add(withReason(c, "所结算商品不在适用范围内"));
            else if (eligible < c.threshold()) unavailable.add(withReason(c, String.format("还差 ¥%.2f 可用", (c.threshold() - eligible) / 100.0)));
            else available.add(couponMap(c));
        }
        UserCouponDTO best = coupons.stream().filter(c -> {
            int eligible = Pricing.eligibleAmount(toPricing(lines), c);
            return eligible > 0 && eligible >= c.threshold();
        }).max(Comparator.comparingInt(c -> Pricing.couponDeduct(c, Pricing.eligibleAmount(toPricing(lines), c)))).orElse(null);

        boolean couponIdGiven = body.get("couponId") != null;
        Long chosenId = couponIdGiven && !String.valueOf(body.get("couponId")).isEmpty()
                ? Long.parseLong(String.valueOf(body.get("couponId"))) : null;
        if (!couponIdGiven && best != null) chosenId = best.id();
        final Long selectedId = chosenId;
        UserCouponDTO selected = couponIdGiven && selectedId != null
                ? coupons.stream().filter(c -> c.id().equals(selectedId)).findFirst().orElse(null) : best;
        if (selected != null) { // 不可用的券不让选中
            int eligible = Pricing.eligibleAmount(toPricing(lines), selected);
            if (eligible <= 0 || eligible < selected.threshold()) selected = null;
        }
        UserBrief brief = userClient.user(userId).unwrap();
        Pricing.Calc calc = calc(lines, selected, body.get("pointsUsed") == null ? 0 : ((Number) body.get("pointsUsed")).intValue(), brief.points());

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("items", lines.stream().map(l -> Map.ofEntries(
                Map.entry("skuId", String.valueOf(l.sku().skuId())), Map.entry("spuId", String.valueOf(l.sku().spuId())),
                Map.entry("name", l.sku().name()), Map.entry("specText", l.sku().specText()),
                Map.entry("art", l.sku().art()), Map.entry("tint", l.sku().tint()),
                Map.entry("price", l.sku().price()), Map.entry("count", l.count()))).toList());
        preview.put("address", defaultAddress(userClient.addresses(userId).unwrap()));
        preview.put("availableCoupons", available);
        preview.put("unavailableCoupons", unavailable);
        preview.put("bestCouponId", best == null ? null : String.valueOf(best.id()));
        preview.put("selectedCouponId", selected == null ? null : String.valueOf(selected.id()));
        preview.put("fullReductionRules", List.of(Map.of("threshold", Pricing.FULL_THRESHOLD, "reduce", Pricing.FULL_REDUCE)));
        preview.put("pointsBalance", brief.points());
        preview.put("pointsRate", Pricing.POINTS_RATE);
        preview.put("maxPointsRatio", Pricing.MAX_POINTS_RATIO);
        preview.put("maxPoints", calc.maxPoints());
        preview.put("price", calc.price());
        String token = String.valueOf(IdGenerator.nextId());
        redis.opsForValue().set("order:submit:" + token, String.valueOf(userId), Duration.ofMinutes(15));
        preview.put("submitToken", token);
        return preview;
    }

    private List<Pricing.Line> toPricing(List<Line> lines) {
        return lines.stream().map(l -> new Pricing.Line(l.sku(), l.count())).toList();
    }

    private Map<String, Object> withReason(UserCouponDTO c, String reason) {
        Map<String, Object> m = couponMap(c);
        m.put("reason", reason);
        return m;
    }

    private Map<String, Object> defaultAddress(List<AddressDTO> addresses) {
        if (addresses == null || addresses.isEmpty()) return null;
        AddressDTO a = addresses.stream().filter(AddressDTO::isDefault).findFirst().orElse(addresses.get(0));
        return assembler.addressMap(a);
    }

    /* ============================ 提交 ============================ */
    // 全局事务超时 10s（Seata 默认 60s）：TM 来不及上报回滚的在途事务（如发起它的 TC 宕机）由 TC 超时回滚，
    // 期间它持有的 SKU 全局锁会挡住同一 SKU 的所有新订单，超时即这段窗口的上限。压测下提交 p95 ≈ 1.3s、最慢 ≈ 3s。
    @GlobalTransactional(rollbackFor = Exception.class, name = "order-submit", timeoutMills = 10_000)
    public Map<String, Object> submit(long userId, Map<String, Object> body) {
        String token = String.valueOf(body.get("submitToken"));
        Boolean first = redis.delete("order:submit:" + token);
        if (!Boolean.TRUE.equals(first)) throw new BizException(5001, "请勿重复提交订单");
        UserBrief brief = userClient.user(userId).unwrap();
        if (brief.creditScore() < 300 || brief.blocked()) throw new BizException(5009, "账户存在异常行为，已被风控拦截");
        AddressDTO address = userClient.addresses(userId).unwrap().stream()
                .filter(a -> String.valueOf(a.id()).equals(String.valueOf(body.get("addressId")))).findFirst()
                .orElseThrow(() -> new BizException(5002, "请选择收货地址"));

        List<Line> lines = buildLines((List<Map<String, Object>>) body.getOrDefault("items", List.of()));
        UserCouponDTO coupon = null;
        if (body.get("couponId") != null && !String.valueOf(body.get("couponId")).isEmpty()) {
            long couponId = Long.parseLong(String.valueOf(body.get("couponId")));
            coupon = marketing.userCoupons(userId).unwrap().stream()
                    .filter(c -> c.id().equals(couponId) && "UNUSED".equals(c.status())).findFirst()
                    .orElseThrow(() -> new BizException(4005, "优惠券不可用"));
        }
        Pricing.Calc calc = calc(lines, coupon, body.get("pointsUsed") == null ? 0 : ((Number) body.get("pointsUsed")).intValue(), brief.points());
        // 快照库存预检：明显不够时在开任何分支前失败（扣减本身仍以商品服务的条件 UPDATE 为准）
        for (Line l : lines) {
            if (l.sku().stock() < l.count()) throw new BizException(3001, "「" + l.sku().name() + "」库存不足");
        }

        long orderId = IdGenerator.nextId(userId & 3);
        String shard = Shard.of(orderId);
        Instant now = Instant.now();
        OrderRow o = new OrderRow();
        o.setId(orderId);
        o.setUserId(userId);
        o.setStatus(OrderStatus.PENDING_PAYMENT);
        o.setSource("NORMAL");
        o.setGoodsAmount(calc.price().goodsAmount());
        o.setFreight(calc.price().freight());
        o.setFullReduction(calc.price().fullReduction());
        o.setCouponDeduct(calc.price().couponDeduct());
        o.setPointsDeduct(calc.price().pointsDeduct());
        o.setPayAmount(calc.price().payAmount());
        o.setCouponId(coupon == null ? null : coupon.id());
        o.setPointsUsed(calc.pointsUsed());
        o.setRemark((String) body.get("remark"));
        o.setAddressJson(assembler.addressJson(address));
        o.setReceiver(address.receiver());
        o.setPhone(address.phone());
        o.setPayDeadline(now.plus(Duration.ofMinutes(ORDER_TTL_MINUTES)));
        o.setCreatedAt(now);
        o.setUpdatedAt(now);
        List<OrderItemRow> items = new ArrayList<>();
        for (Line l : lines) {
            OrderItemRow i = new OrderItemRow();
            i.setId(IdGenerator.nextId());
            i.setOrderId(orderId);
            i.setUserId(userId);
            i.setSkuId(l.sku().skuId());
            i.setSpuId(l.sku().spuId());
            i.setName(l.sku().name());
            i.setSpecText(l.sku().specText());
            i.setArt(l.sku().art());
            i.setTint(l.sku().tint());
            i.setPrice(l.sku().price());
            i.setCount(l.count());
            items.add(i);
        }

        // Seata 分支顺序（Phase 5）：锁券 → 扣积分 → 建单（主表 + 明细同一本地事务 = 一个分支）→ 扣库存放最后。
        // AT 的全局行锁从分支注册持有到全局提交：扣库存越晚，热点 SKU 行被锁住的时间越短；任一失败全局回滚。
        if (coupon != null) marketing.lock(new CouponOpReq(userId, coupon.id(), null)).unwrap();
        if (calc.pointsUsed() > 0) userClient.changePoints(new PointsChangeReq(userId, -calc.pointsUsed(), "订单积分抵扣")).unwrap();
        tx.executeWithoutResult(st -> {
            orderMapper.insertOrder(shard, o);
            for (OrderItemRow i : items) orderMapper.insertItem(shard, i);
        });
        product.deduct(new StockOpReq(orderId, lines.stream().map(l -> new StockItem(l.sku().skuId(), l.count())).toList())).unwrap();
        // 下单成功移除购物车中已购商品（Redis 不参与 Seata，失败可接受）
        if (Boolean.TRUE.equals(body.get("fromCart"))) {
            try {
                redis.opsForHash().delete("cart:" + userId, lines.stream().map(l -> String.valueOf(l.sku().skuId())).toArray());
            } catch (Exception ignored) {
            }
        }
        rabbit.convertAndSend(Const.MQ_ORDER_DELAY_EXCHANGE, Const.MQ_ORDER_DELAY_30M_KEY, new OrderCloseMsg(orderId, userId));
        ops.log("ORDER", "[下单] 订单 " + orderId + " 创建成功 → 投递延迟消息 order.close.delay（TTL 30min）");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", String.valueOf(orderId));
        out.put("payAmount", o.getPayAmount());
        out.put("payDeadline", o.getPayDeadline());
        return out;
    }

    /* ============================ 查询 ============================ */
    public PageResult<OrderVO> list(long userId, String status, String keyword, int page, int size) {
        List<String> statuses = null;
        if (status != null && !status.isEmpty()) statuses = "CLOSED".equals(status) ? List.of("CLOSED", "CANCELLED") : List.of(status);
        OrderMapper.ListQuery q = new OrderMapper.ListQuery();
        q.setTables(List.of(Shard.of(userId)));
        q.setUserId(userId);
        q.setStatuses(statuses);
        q.setKeyword(keyword == null || keyword.isEmpty() ? null : "%" + keyword + "%");
        q.setAdminKeyword(false);
        return paged(q, page, size);
    }

    public PageResult<OrderVO> adminList(String status, String source, String keyword, int page, int size) {
        OrderMapper.ListQuery q = new OrderMapper.ListQuery();
        q.setTables(Shard.ALL);
        q.setStatuses(status == null || status.isEmpty() ? null : List.of(status));
        // 空串视为未传（JS 客户端常把空参数一并发送），避免拼出 source = '' 恒假条件
        q.setSource(source == null || source.isEmpty() ? null : source);
        applyAdminKeyword(q, keyword);
        q.setAdminKeyword(true);
        return paged(q, page, size);
    }

    /**
     * OM-015：按关键词形态分流，保证每条分支都能命中索引（不再 %kw% 全分片扫描）。
     * 15 位以上纯数字 → 订单号主键精确；其余纯数字 → 手机号前缀（idx_phone）；否则 → 收货人前缀（idx_receiver）。
     */
    static void applyAdminKeyword(OrderMapper.ListQuery q, String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) return;
        if (kw.matches("\\d{15,19}")) {
            q.setKeywordMode("ID");
            q.setKeywordId(Long.parseLong(kw));
            q.setKeyword(kw);
            return;
        }
        q.setKeywordMode(kw.matches("\\d{1,11}") ? "PHONE" : "RECEIVER");
        // LIKE 转义（ESCAPE '!'）：用户输入的 % _ ! 按字面匹配
        q.setKeyword(kw.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }

    private PageResult<OrderVO> paged(OrderMapper.ListQuery q, int page, int size) {
        q.setOffset((page - 1) * size);
        q.setSize(size);
        int total = orderMapper.count(q);
        List<OrderVO> records = orderMapper.list(q).stream().map(this::toVO).toList();
        return new PageResult<>(records, total, page, size);
    }

    public Map<String, Integer> counts(long userId) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (OrderMapper.StatusCount sc : orderMapper.statusCounts(List.of(Shard.of(userId)), userId)) m.put(sc.getStatus(), sc.getCnt());
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String s : List.of("PENDING_PAYMENT", "PAID", "SHIPPED", "COMPLETED")) out.put(s, m.getOrDefault(s, 0));
        return out;
    }

    public OrderVO get(long userId, long id, boolean admin) {
        OrderRow o = findAny(id);
        if (o == null || (!o.getUserId().equals(userId) && !admin)) throw new BizException(5004, "订单不存在");
        return toVO(o);
    }

    private OrderRow findAny(long id) {
        OrderRow o = orderMapper.findOrder(Shard.of(id), id);
        return o != null ? o : orderMapper.findOrder(Shard.ARCHIVE, id);
    }

    private OrderVO toVO(OrderRow o) {
        return assembler.toVO(o, orderMapper.findItems(o.getSrc(), List.of(o.getId())));
    }

    /* ============================ 状态机 ============================ */
    public boolean cancel(long userId, long id) {
        OrderRow o = findAny(id);
        if (o == null || !o.getUserId().equals(userId)) throw new BizException(5004, "订单不存在");
        if (!OrderStatus.PENDING_PAYMENT.equals(o.getStatus())) throw new BizException(5005, "当前状态不可取消");
        close(o, "用户主动取消", OrderStatus.CANCELLED);
        return true;
    }

    public Map<String, Object> confirm(long userId, long id) {
        OrderRow o = findAny(id);
        if (o == null || !o.getUserId().equals(userId)) throw new BizException(5004, "订单不存在");
        if (!OrderStatus.SHIPPED.equals(o.getStatus())) throw new BizException(5006, "当前状态不可确认收货");
        Logistics.Info lg = assembler.logistics(o.getLogisticsJson());
        if (lg != null && !"SIGNED".equals(lg.state())) {
            lg = lg.prepend(new Logistics.Trace(Logistics.iso(Instant.now()), "SIGNED", "用户已确认收货", cityOf(o)));
        }
        int rows = orderMapper.complete(o.getSrc(), id, Instant.now(), lg == null ? null : assembler.write(lg));
        if (rows > 0) {
            if (o.getCouponId() != null) marketing.use(new CouponOpReq(userId, o.getCouponId(), id)).unwrap();
            int earned = o.getPayAmount() / 100;
            if (earned > 0) userClient.changePoints(new PointsChangeReq(userId, earned, "订单 " + id + " 确认收货奖励")).unwrap();
            return Map.of("earnedPoints", earned);
        }
        throw new BizException(5006, "当前状态不可确认收货");
    }

    private String cityOf(OrderRow o) {
        try {
            Map<String, Object> a = assembler.read(o.getAddressJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
            return String.valueOf(a.getOrDefault("city", ""));
        } catch (Exception e) {
            return "";
        }
    }

    /** MQ 关单入口：找到订单行再走 CAS 关单 */
    public void closeById(long orderId, String reason, String status) {
        OrderRow o = findAny(orderId);
        if (o != null) close(o, reason, status);
    }

    /**
     * 关单（取消 / 超时 / 后台过期）：CAS 关单后回补库存、释放优惠券、退还积分。
     * CAS（PENDING_PAYMENT → status）是补偿只执行一次的唯一保证：只有把状态真正改走的那一次调用才回补，
     * MQ 延迟消息、DLQ 重试、超时扫描、用户取消并发或重复到达时其余调用 rows=0 直接返回。
     * （修复前这里误传 PENDING_PAYMENT，状态从未改变 → 每分钟超时扫描反复回补同一批订单的库存，见 PHASE4-REPORT。）
     */
    public void close(OrderRow o, String reason, String status) {
        if (!OrderStatus.CLOSED.equals(status) && !OrderStatus.CANCELLED.equals(status))
            throw new IllegalArgumentException("close status must be CLOSED/CANCELLED: " + status);
        int rows = orderMapper.closeIfPending(o.getSrc(), o.getId(), status, Instant.now(), reason);
        if (rows == 0) return;
        if ("NORMAL".equals(o.getSource())) {
            List<OrderItemRow> items = orderMapper.findItems(o.getSrc(), List.of(o.getId()));
            restoreStock(o.getId(), items.stream().map(i -> new StockItem(i.getSkuId(), i.getCount())).toList());
        }
        if (o.getCouponId() != null) {
            try {
                marketing.release(new CouponOpReq(o.getUserId(), o.getCouponId(), o.getId())).unwrap();
            } catch (Exception ignored) {
            }
        }
        if (o.getPointsUsed() > 0) {
            try {
                userClient.changePoints(new PointsChangeReq(o.getUserId(), o.getPointsUsed(), "订单 " + o.getId() + " 关闭，积分退回")).unwrap();
            } catch (Exception ignored) {
            }
        }
        ops.log("MQ", "[DLX 消费] 订单 " + o.getId() + " " + reason + "，已回补库存/释放优惠券");
        notifier.orderStatus(o.getUserId(), o.getId(), status, reason);
    }

    /** 关单回补的同步重试间隔：商品侧单次最多等全局锁 4s，三次覆盖 order-submit 的 10s 全局超时（测试里置零） */
    long[] restoreBackoffMs = {500, 1500};
    /** 同步重试仍失败 → 转后台重试（Redis 延迟集合，Jobs 每 5s 扫一次），每次失败后的等待；全部失败才告警（共约 2 分钟） */
    long[] restoreRetryDelayMs = {5_000, 15_000, 30_000, 60_000};
    static final String RESTORE_RETRY = "order:restore:retry";
    static final String RESTORE_RETRY_N = "order:restore:retry:n";

    /**
     * 关单回补库存：CAS 已把订单改走，这里失败就再没有别的路径会回补，所以重试。
     * 商品侧按订单记 RESTORE 流水，重复调用（包括「超时但其实已成功」的那次）不会多补。
     * 典型失败是同一 SKU 行被某个未决的下单全局事务占着全局锁（如 TC 宕机后等超时回滚），商品侧 @GlobalLock 等不到即失败。
     * 同步只试 3 次（不能让用户取消请求 / MQ 消费线程长时间阻塞）；TC 故障切换可能超过这个窗口（HA 演练实测约 20s），
     * 所以之后转入 Redis 延迟集合由后台继续重试，进程重启也不丢。
     */
    void restoreStock(long orderId, List<StockItem> items) {
        Exception last = null;
        for (int attempt = 0; attempt <= restoreBackoffMs.length; attempt++) {
            try {
                product.restore(new StockOpReq(orderId, items)).unwrap();
                return;
            } catch (Exception e) {
                last = e;
                if (attempt == restoreBackoffMs.length) break;
                try {
                    Thread.sleep(restoreBackoffMs[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        String reason = last == null ? "" : last.getMessage();
        try {
            redis.opsForZSet().add(RESTORE_RETRY, String.valueOf(orderId), System.currentTimeMillis() + restoreRetryDelayMs[0]);
            ops.log("ORDER", "[关单补偿延后] 订单 " + orderId + " 库存回补同步重试失败，转后台重试：" + reason);
        } catch (Exception e) {
            ops.log("ALERT", "[关单补偿失败] 订单 " + orderId + " 库存回补重试耗尽且无法登记后台重试，需人工按 stock_ledger 核对：" + reason);
        }
    }

    /** 后台重试到期的关单回补（Jobs 定时调用）；返回本轮处理的订单数 */
    public int retryPendingRestores() {
        return retryPendingRestores(System.currentTimeMillis());
    }

    int retryPendingRestores(long now) {
        Set<String> due = redis.opsForZSet().rangeByScore(RESTORE_RETRY, 0, now, 0, 50);
        if (due == null || due.isEmpty()) return 0;
        int handled = 0;
        for (String member : due) {
            Long removed = redis.opsForZSet().remove(RESTORE_RETRY, member);
            if (removed == null || removed == 0) continue; // 被另一个实例领走
            handled++;
            long orderId = Long.parseLong(member);
            try {
                OrderRow o = findAny(orderId);
                if (o == null) throw new IllegalStateException("订单不存在");
                List<OrderItemRow> items = orderMapper.findItems(o.getSrc(), List.of(orderId));
                product.restore(new StockOpReq(orderId, items.stream().map(i -> new StockItem(i.getSkuId(), i.getCount())).toList())).unwrap();
                redis.opsForHash().delete(RESTORE_RETRY_N, member);
                ops.log("ORDER", "[关单补偿完成] 订单 " + orderId + " 库存回补后台重试成功");
            } catch (Exception e) {
                long n = redis.opsForHash().increment(RESTORE_RETRY_N, member, 1);
                if (n < restoreRetryDelayMs.length) {
                    redis.opsForZSet().add(RESTORE_RETRY, member, now + restoreRetryDelayMs[(int) n]);
                } else {
                    redis.opsForHash().delete(RESTORE_RETRY_N, member);
                    ops.log("ALERT", "[关单补偿失败] 订单 " + orderId + " 库存回补重试耗尽，需人工按 stock_ledger 核对：" + e.getMessage());
                }
            }
        }
        return handled;
    }


    /** 支付成功消费（MQ pay.exchange → order.pay.success.queue） */
    public void onPaySuccess(PaySuccessMsg msg) {
        OrderRow o = findAny(msg.orderId());
        if (o == null) return;
        int rows = orderMapper.markPaid(o.getSrc(), o.getId(), msg.paidAt(), msg.channel(), msg.paymentNo());
        if (rows > 0) notifier.orderStatus(o.getUserId(), o.getId(), OrderStatus.PAID, "支付成功，商家正在备货");
    }

    /* ============================ 后台 ============================ */
    public Map<String, Object> ship(long id, String company, String trackingNo) {
        OrderRow o = findAny(id);
        if (o == null) throw new BizException(5004, "订单不存在");
        if (!OrderStatus.PAID.equals(o.getStatus())) throw new BizException(9001, "仅待发货订单可发货");
        String comp = company == null || company.isEmpty() ? "顺丰速运" : company;
        String no = trackingNo == null || trackingNo.isEmpty() ? "SF" + String.valueOf(System.currentTimeMillis()).substring(1) : trackingNo;
        Logistics.Info lg = new Logistics.Info(comp, no, "COLLECTED", List.of(
                new Logistics.Trace(Logistics.iso(Instant.now()), "COLLECTED", comp + "已揽收，快件正在发往【西安集散中心】", "西安市")));
        int rows = orderMapper.ship(o.getSrc(), id, Instant.now(), assembler.write(lg));
        if (rows == 0) throw new BizException(9001, "仅待发货订单可发货");
        ops.log("ORDER", "[发货] 订单 " + id + " 已发货，订阅快递鸟轨迹推送 " + no);
        notifier.orderStatus(o.getUserId(), id, OrderStatus.SHIPPED, "您的订单已发货");
        Map<String, Object> trace = new LinkedHashMap<>();
        Logistics.Trace t = lg.traces().get(0);
        trace.put("time", t.time());
        trace.put("state", t.state());
        trace.put("desc", t.desc());
        trace.put("location", t.location());
        notifier.send(o.getUserId(), Map.of("type", "LOGISTICS_UPDATE", "orderId", String.valueOf(id), "trace", trace));
        return Map.of("trackingNo", no);
    }

    /** 仅测试/演示：立即触发延迟关单 */
    public boolean expireNow(long id) {
        OrderRow o = findAny(id);
        if (o == null) throw new BizException(5004, "订单不存在");
        if (!OrderStatus.PENDING_PAYMENT.equals(o.getStatus())) throw new BizException(9002, "仅待支付订单可模拟超时");
        orderMapper.expireNow(o.getSrc(), id, Instant.now().minusSeconds(1));
        close(o, "支付超时，订单自动关闭", OrderStatus.CLOSED);
        return true;
    }

    public Map<String, Object> dashboard() {
        Map<String, Integer> statusCount = new LinkedHashMap<>();
        for (String s : List.of("PENDING_PAYMENT", "PAID", "SHIPPED", "COMPLETED", "CANCELLED", "CLOSED")) statusCount.put(s, 0);
        for (OrderMapper.StatusCount sc : orderMapper.statusCounts(Shard.ALL, null)) statusCount.merge(sc.getStatus(), sc.getCnt(), Integer::sum);
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        Instant from = now.minusDays(6).toLocalDate().atStartOfDay(ZONE).toInstant();
        Map<String, int[]> byDay = new LinkedHashMap<>(); // day -> [orders, gmv]
        Map<String, int[]> byDayAll = new LinkedHashMap<>();
        for (OrderMapper.DailyStat d : orderMapper.dailyStats(Shard.ALL, from)) {
            boolean paid = List.of("PAID", "SHIPPED", "COMPLETED").contains(d.getStatus());
            byDay.computeIfAbsent(d.getDay(), k -> new int[2]);
            if (paid) {
                byDay.get(d.getDay())[0] += d.getCnt();
                byDay.get(d.getDay())[1] += d.getAmount();
            }
            byDayAll.computeIfAbsent(d.getDay(), k -> new int[2]);
            byDayAll.get(d.getDay())[0] += d.getCnt();
            byDayAll.get(d.getDay())[1] += d.getAmount();
        }
        String today = now.toLocalDate().toString();
        int todayOrders = byDayAll.getOrDefault(today, new int[2])[0];
        int todayGmv = byDay.getOrDefault(today, new int[2])[1];
        List<Map<String, Object>> last7 = new ArrayList<>();
        for (int k = 6; k >= 0; k--) {
            String day = now.minusDays(k).toLocalDate().toString();
            int[] v = byDay.getOrDefault(day, new int[2]);
            ZonedDateTime d = now.minusDays(k);
            last7.add(Map.of("date", d.getMonthValue() + "/" + d.getDayOfMonth(), "gmv", v[1], "orders", v[0]));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("todayGmv", todayGmv);
        out.put("todayOrders", todayOrders);
        out.put("pendingShip", statusCount.get("PAID"));
        out.put("conversionRate", todayOrders == 0 ? 0 : (double) byDay.getOrDefault(today, new int[2])[0] / todayOrders);
        out.put("statusCount", statusCount);
        out.put("last7Days", last7);
        return out;
    }

    /* ============================ inner ============================ */
    public OrderBrief brief(long id) {
        OrderRow o = findAny(id);
        if (o == null) throw new BizException(5004, "订单不存在");
        return new OrderBrief(o.getId(), o.getUserId(), o.getStatus(), o.getPayAmount(), o.getPayDeadline());
    }

    /** 订单商品行（skuId+count），退款 DONE 时由支付服务用于回补库存 */
    public List<StockItem> items(long id) {
        OrderRow o = findAny(id);
        if (o == null) throw new BizException(5004, "订单不存在");
        return orderMapper.findItems(o.getSrc(), List.of(id)).stream()
                .map(i -> new StockItem(i.getSkuId(), i.getCount()))
                .toList();
    }

    /**
     * 售后状态流转（docs/refund-design.md §3）：
     * REFUND_APPLYING（审批中）→ 只允许自 PAID/SHIPPED/COMPLETED 进入；
     * REFUNDED / prev_status 回退：由支付服务驱动，from 非空时 CAS。
     */
    public boolean refundTransition(long id, String from, String to) {
        OrderRow o = findAny(id);
        if (o == null) throw new BizException(5004, "订单不存在");
        boolean valid = switch (to) {
            case OrderStatus.REFUND_APPLYING -> List.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.COMPLETED).contains(o.getStatus());
            case OrderStatus.REFUNDED -> OrderStatus.REFUND_APPLYING.equals(o.getStatus());
            // 回退到售后前的状态：from 必须一致且 to 必须是上述三个目标态之一
            case OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.COMPLETED -> OrderStatus.REFUND_APPLYING.equals(o.getStatus());
            default -> false;
        };
        if (!valid) throw new BizException(5008, "订单状态不可流转到 " + to);
        if (from != null && !from.equals(o.getStatus())) throw new BizException(5008, "订单当前状态非 " + from + "，流转被拒");
        int rows = orderMapper.refundState(o.getSrc(), id, from, to, Instant.now());
        return rows > 0;
    }

    /**
     * 秒杀下单（幂等）：10 分钟延迟关单；发货/支付流程与普通单一致。
     * 本地事务即可（OM-025）：只写订单库两张表，库存/一人一单已在 seckill 侧落定且不在同一全局事务里，
     * 原 @GlobalTransactional 只是给单分支加了 TC 往返 + undo_log，压测中是落单链路的主要耗时之一。
     */
    @Transactional(rollbackFor = Exception.class)
    public long createSeckill(SeckillOrderReq req) {
        OrderRow exists = orderMapper.findOrder(Shard.of(req.orderId()), req.orderId());
        if (exists != null) return req.orderId();
        SkuInfo sku = seckillSku(req.skuId());
        // 地址只取一次：指定地址 → 默认地址 → 任一地址
        List<AddressDTO> addrs = userClient.addresses(req.userId()).unwrap();
        AddressDTO address = addrs.stream()
                .filter(a -> req.addressId() != null && String.valueOf(a.id()).equals(String.valueOf(req.addressId())))
                .findFirst()
                .or(() -> addrs.stream().filter(AddressDTO::isDefault).findFirst())
                .or(() -> addrs.stream().findFirst())
                .orElse(null);
        Instant now = Instant.now();
        OrderRow o = new OrderRow();
        o.setId(req.orderId());
        o.setUserId(req.userId());
        o.setStatus(OrderStatus.PENDING_PAYMENT);
        o.setSource("SECKILL");
        o.setGoodsAmount(sku.price());
        o.setFreight(0);
        o.setSeckillDeduct(sku.price() - req.seckillPrice());
        o.setPayAmount(req.seckillPrice());
        o.setPointsUsed(0);
        o.setActivityId(req.activityId());
        o.setPayDeadline(now.plus(Duration.ofMinutes(SECKILL_TTL_MINUTES)));
        if (address != null) {
            o.setAddressJson(assembler.addressJson(address));
            o.setReceiver(address.receiver());
            o.setPhone(address.phone());
        } else {
            Map<String, Object> temp = new LinkedHashMap<>();
            UserBrief u = userClient.user(req.userId()).unwrap();
            temp.put("id", "TEMP");
            temp.put("receiver", u.nickname());
            temp.put("phone", u.phone());
            temp.put("province", "");
            temp.put("city", "");
            temp.put("district", "");
            temp.put("detail", "请在订单中补充收货地址");
            temp.put("isDefault", false);
            o.setAddressJson(assembler.write(temp));
            o.setReceiver(u.nickname());
            o.setPhone(u.phone());
        }
        o.setCreatedAt(now);
        o.setUpdatedAt(now);
        String shard = Shard.of(req.orderId());
        try {
            orderMapper.insertOrder(shard, o);
        } catch (DuplicateKeyException dup) {
            return req.orderId(); // 并发重试已落库：幂等返回
        }
        OrderItemRow i = new OrderItemRow();
        i.setId(IdGenerator.nextId());
        i.setOrderId(req.orderId());
        i.setUserId(req.userId());
        i.setSkuId(sku.skuId());
        i.setSpuId(sku.spuId());
        i.setName(sku.name());
        i.setSpecText(sku.specText());
        i.setArt(sku.art());
        i.setTint(sku.tint());
        i.setPrice(sku.price());
        i.setCount(1);
        orderMapper.insertItem(shard, i);
        rabbit.convertAndSend(Const.MQ_ORDER_DELAY_EXCHANGE, Const.MQ_ORDER_DELAY_10M_KEY, new OrderCloseMsg(req.orderId(), req.userId()));
        ops.log("SECKILL", "[消费] 秒杀订单 " + req.orderId() + " 落库成功，10 分钟延迟关单");
        return req.orderId();
    }

    private SkuInfo seckillSku(long skuId) {
        long now = System.currentTimeMillis();
        CachedSku c = seckillSkus.get(skuId);
        if (c != null && now - c.at() < SKU_TTL_MS) return c.sku();
        SkuInfo sku = product.skus(List.of(skuId)).unwrap().stream().findFirst()
                .orElseThrow(() -> new BizException(2001, "商品不存在"));
        if (seckillSkus.size() > 1024) seckillSkus.entrySet().removeIf(e -> now - e.getValue().at() >= SKU_TTL_MS);
        seckillSkus.put(skuId, new CachedSku(sku, now));
        return sku;
    }
}
