package com.orangemall.web;

import com.orangemall.common.Const;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** 读取网关透传头写入 UserContext 与 MDC(traceId)，请求结束清理 */
public class ContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uid = req.getHeader(Const.H_USER_ID);
        String trace = req.getHeader(Const.H_TRACE);
        if (trace == null || trace.isBlank()) trace = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Long userId = null;
        try {
            if (uid != null && !uid.isBlank()) userId = Long.parseLong(uid);
        } catch (NumberFormatException ignored) {
        }
        UserContext.set(userId, req.getHeader(Const.H_USER_ROLE), trace);
        MDC.put(Const.MDC_TRACE, trace);
        try {
            chain.doFilter(req, res);
        } finally {
            UserContext.clear();
            MDC.remove(Const.MDC_TRACE);
        }
    }
}
