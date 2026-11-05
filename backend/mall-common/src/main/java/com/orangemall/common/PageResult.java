package com.orangemall.common;

import java.util.List;

/** 分页响应：total 用 int（Long 会被全局序列化为字符串，只有雪花 ID 才用 Long） */
public record PageResult<T>(List<T> records, int total, int page, int size) {
    public static <T> PageResult<T> of(List<T> all, int page, int size) {
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        return new PageResult<>(from >= to ? List.of() : List.copyOf(all.subList(from, to)), all.size(), page, size);
    }
}
