package com.orangemall.web;

import com.orangemall.common.BizException;

/**
 * 当前请求用户（来自网关透传的 X-User-Id / X-User-Role，下游服务不再解析 JWT）。
 */
public final class UserContext {
    record Ctx(Long userId, String role, String traceId) {
    }

    private static final ThreadLocal<Ctx> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    static void set(Long userId, String role, String traceId) {
        HOLDER.set(new Ctx(userId, role, traceId));
    }

    static void clear() {
        HOLDER.remove();
    }

    /** 未登录返回 null（optional 鉴权接口用） */
    public static Long userId() {
        Ctx c = HOLDER.get();
        return c == null ? null : c.userId();
    }

    public static String role() {
        Ctx c = HOLDER.get();
        return c == null ? null : c.role();
    }

    public static String traceId() {
        Ctx c = HOLDER.get();
        return c == null ? null : c.traceId();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(role());
    }

    /** user 级接口：未登录抛 401 */
    public static long requireUser() {
        Long id = userId();
        if (id == null) throw new BizException(401, "请先登录");
        return id;
    }

    /** admin 级接口：未登录 401、非管理员 403（网关已拦一道，这里兜底） */
    public static long requireAdmin() {
        long id = requireUser();
        if (!isAdmin()) throw new BizException(403, "无权限访问");
        return id;
    }
}
