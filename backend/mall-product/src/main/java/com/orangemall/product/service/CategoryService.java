package com.orangemall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.orangemall.common.BizException;
import com.orangemall.product.entity.Category;
import com.orangemall.product.entity.Spu;
import com.orangemall.product.mapper.CategoryMapper;
import com.orangemall.product.mapper.SpuMapper;
import com.orangemall.product.vo.CategoryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 类目树：邻接表 → 树。二级缓存：Caffeine（进程内，10min）→ Redis product:category:tree（1h + 抖动）→ MySQL。
 * 后台增删改后：DEL Redis + 清本地 Caffeine，并通过 Redis Pub/Sub 广播让其它实例清理本地缓存。
 */
@Slf4j
@Service
public class CategoryService {
    public static final String K_TREE = "product:category:tree";
    public static final String CH_INVALIDATE = "product:category:invalidate";

    /** 不可变快照：树 + 扁平索引 */
    public record Snapshot(List<CategoryVO> tree, Map<Integer, CategoryVO> byId) {
    }

    private final CategoryMapper categoryMapper;
    private final SpuMapper spuMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Cache<String, Snapshot> local = Caffeine.newBuilder()
            .maximumSize(1).expireAfterWrite(Duration.ofMinutes(10)).build();

    public CategoryService(CategoryMapper categoryMapper, SpuMapper spuMapper, StringRedisTemplate redis,
                           ObjectMapper mapper, RedisMessageListenerContainer container) {
        this.categoryMapper = categoryMapper;
        this.spuMapper = spuMapper;
        this.redis = redis;
        this.mapper = mapper;
        container.addMessageListener((msg, pattern) -> {
            local.invalidateAll();
            log.info("category local cache invalidated by broadcast");
        }, new ChannelTopic(CH_INVALIDATE));
    }

    public Snapshot snapshot() {
        return local.get("tree", k -> load());
    }

    public List<CategoryVO> tree() {
        return snapshot().tree();
    }

    private Snapshot load() {
        List<CategoryVO> tree = null;
        try {
            String json = redis.opsForValue().get(K_TREE);
            if (json != null) tree = mapper.readValue(json, new TypeReference<List<CategoryVO>>() {});
        } catch (Exception e) {
            log.warn("read category cache failed: {}", e.getMessage());
        }
        if (tree == null) {
            tree = buildTree(categoryMapper.selectList(null));
            try {
                long ttl = 3600 + ThreadLocalRandom.current().nextInt(600);
                redis.opsForValue().set(K_TREE, mapper.writeValueAsString(tree), Duration.ofSeconds(ttl));
            } catch (Exception e) {
                log.warn("write category cache failed: {}", e.getMessage());
            }
        }
        Map<Integer, CategoryVO> byId = new HashMap<>();
        Deque<CategoryVO> stack = new ArrayDeque<>(tree);
        while (!stack.isEmpty()) {
            CategoryVO c = stack.pop();
            byId.put(c.getId(), c);
            if (c.getChildren() != null) stack.addAll(c.getChildren());
        }
        return new Snapshot(tree, byId);
    }

    /** 邻接表 → 树（每个节点都有 children 数组，按 sort 升序；与 Mock buildTree 一致） */
    public static List<CategoryVO> buildTree(List<Category> list) {
        Map<Integer, CategoryVO> map = new LinkedHashMap<>();
        for (Category c : list) {
            CategoryVO v = new CategoryVO();
            v.setId(c.getId());
            v.setParentId(c.getParentId());
            v.setName(c.getName());
            v.setIcon(c.getIcon());
            v.setSort(c.getSort());
            v.setChildren(new ArrayList<>());
            map.put(c.getId(), v);
        }
        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO c : map.values()) {
            if (c.getParentId() == 0) roots.add(c);
            else {
                CategoryVO p = map.get(c.getParentId());
                if (p != null) p.getChildren().add(c);
            }
        }
        sort(roots);
        return roots;
    }

    private static void sort(List<CategoryVO> arr) {
        arr.sort(Comparator.comparingInt(CategoryVO::getSort));
        arr.forEach(c -> sort(c.getChildren()));
    }

    /** 自身 + 所有后代 ID（任意层级类目搜索） */
    public Set<Integer> descendantIds(int id) {
        Set<Integer> out = new LinkedHashSet<>();
        out.add(id);
        CategoryVO c = snapshot().byId().get(id);
        if (c != null) {
            Deque<CategoryVO> stack = new ArrayDeque<>(c.getChildren());
            while (!stack.isEmpty()) {
                CategoryVO x = stack.pop();
                out.add(x.getId());
                stack.addAll(x.getChildren());
            }
        }
        return out;
    }

    /** 类目路径（根 → 叶）名称 */
    public List<String> path(Integer categoryId) {
        LinkedList<String> path = new LinkedList<>();
        Map<Integer, CategoryVO> byId = snapshot().byId();
        CategoryVO cur = categoryId == null ? null : byId.get(categoryId);
        int guard = 0;
        while (cur != null && guard++ < 10) {
            path.addFirst(cur.getName());
            cur = cur.getParentId() == 0 ? null : byId.get(cur.getParentId());
        }
        return path;
    }

    public Integer rootOf(Integer categoryId) {
        Map<Integer, CategoryVO> byId = snapshot().byId();
        CategoryVO cur = categoryId == null ? null : byId.get(categoryId);
        int guard = 0;
        while (cur != null && cur.getParentId() != 0 && guard++ < 10) {
            CategoryVO p = byId.get(cur.getParentId());
            if (p == null) break;
            cur = p;
        }
        return cur == null ? null : cur.getId();
    }

    /* ---------------- 后台 ---------------- */

    public List<CategoryVO> adminTree() {
        return buildTree(categoryMapper.selectList(null));
    }

    public synchronized CategoryVO create(Integer parentId, String name) {
        if (name == null || name.isBlank()) throw BizException.of(400, "类目名称不能为空");
        int pid = parentId == null ? 0 : parentId;
        if (pid != 0 && categoryMapper.selectById(pid) == null) throw BizException.of(9003, "类目不存在");
        Category max = categoryMapper.selectOne(new LambdaQueryWrapper<Category>().orderByDesc(Category::getId).last("LIMIT 1"));
        Category c = new Category();
        c.setId(max == null ? 1 : max.getId() + 1);
        c.setParentId(pid);
        c.setName(name.trim());
        c.setSort(99);
        categoryMapper.insert(c);
        invalidate();
        CategoryVO v = new CategoryVO();
        v.setId(c.getId());
        v.setParentId(c.getParentId());
        v.setName(c.getName());
        v.setSort(c.getSort());
        return v;
    }

    public void rename(int id, String name) {
        Category c = categoryMapper.selectById(id);
        if (c == null) throw BizException.of(9003, "类目不存在");
        if (name == null || name.isBlank()) throw BizException.of(400, "类目名称不能为空");
        c.setName(name.trim());
        categoryMapper.updateById(c);
        invalidate();
    }

    public void delete(int id) {
        if (categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getParentId, id)) > 0)
            throw BizException.of(9004, "存在子类目，不能删除");
        if (spuMapper.selectCount(new LambdaQueryWrapper<Spu>().eq(Spu::getCategoryId, id)) > 0)
            throw BizException.of(9005, "类目下存在商品，不能删除");
        categoryMapper.deleteById(id);
        invalidate();
    }

    /** 写库后：DEL 二级缓存 + 清本地一级缓存 + 广播其它实例 */
    public void invalidate() {
        local.invalidateAll();
        try {
            redis.delete(K_TREE);
            redis.convertAndSend(CH_INVALIDATE, "1");
        } catch (Exception e) {
            log.warn("invalidate category cache failed: {}", e.getMessage());
        }
    }
}
