package com.orangemall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orangemall.api.client.MarketingInnerClient;
import com.orangemall.api.client.SeckillInnerClient;
import com.orangemall.product.entity.Notice;
import com.orangemall.product.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** 首页聚合：本服务数据 + 秒杀预览 / 领券预览（下游不可用时降级为空，不影响首页） */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {
    public static final List<String> HOT_KEYWORDS = List.of("iPhone17", "联想笔记本", "运动鞋", "羽绒服", "咖啡机", "双11");
    private static final List<Map<String, String>> BANNERS = List.of(
            banner("B1", "双11 全球好物节", "限时狂欢 低至5折", "品牌好物 · 跨店满减 · 领券再省", "red"),
            banner("B2", "数码焕新季", "旗舰新品 12期免息", "以旧换新 · 至高补贴 2000 元", "violet"),
            banner("B3", "整点秒杀", "每 2 小时一场 限量抢", "一人一单 · 手慢无", "orange"),
            banner("B4", "新人专享", "注册即领 10 元无门槛券", "再送 200 积分 · 积分可抵现", "teal"));
    private static final long DOWNSTREAM_TIMEOUT_MS = 1500;

    private final SeckillInnerClient seckillClient;
    private final MarketingInnerClient marketingClient;
    private final NoticeMapper noticeMapper;
    private final ProductService productService;
    private final ExecutorService homeExecutor;

    private static Map<String, String> banner(String id, String tag, String title, String subtitle, String theme) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tag", tag);
        m.put("title", title);
        m.put("subtitle", subtitle);
        m.put("theme", theme);
        return m;
    }

    public Map<String, Object> home(Long userId) {
        CompletableFuture<Map<String, Object>> seckill = CompletableFuture
                .supplyAsync(() -> seckillClient.preview().unwrap(), homeExecutor)
                .completeOnTimeout(null, DOWNSTREAM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(e -> {
                    log.warn("seckill preview degraded: {}", e.getMessage());
                    return null;
                });
        CompletableFuture<List<Map<String, Object>>> coupons = CompletableFuture
                .supplyAsync(() -> marketingClient.templatesPreview(2, userId).unwrap(), homeExecutor)
                .completeOnTimeout(null, DOWNSTREAM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(e -> {
                    log.warn("coupon preview degraded: {}", e.getMessage());
                    return null;
                });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hotKeywords", HOT_KEYWORDS);
        data.put("notices", noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .orderByDesc(Notice::getDate).orderByAsc(Notice::getId)));
        Map<String, Object> sp = seckill.join();
        data.put("seckillPreview", sp != null ? sp : emptySeckillPreview());
        List<Map<String, Object>> cp = coupons.join();
        data.put("couponPreview", cp != null ? cp : List.of());
        data.put("newArrival", productService.newArrival());
        data.put("banners", BANNERS);
        return data;
    }

    /** 降级：当前 2 小时场次的结束时间 + 空列表（前端倒计时仍可正常展示） */
    static Map<String, Object> emptySeckillPreview() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        ZonedDateTime start = now.truncatedTo(ChronoUnit.DAYS).plusHours(now.getHour() / 2 * 2L);
        Instant end = start.plusHours(2).toInstant();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("endAt", end.toString());
        m.put("items", List.of());
        return m;
    }
}
