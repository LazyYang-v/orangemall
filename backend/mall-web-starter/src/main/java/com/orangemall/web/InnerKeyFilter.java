package com.orangemall.web;

import com.orangemall.common.Const;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 服务间内部接口鉴权：/inner/** 仅放行携带正确 X-Inner-Key 的调用方。
 * 网关不路由 /inner（外部不可达），此 Filter 防的是同一 Docker 网络内的裸调用——
 * 没有它，任何能进内网的容器都能直接调 /inner/points/change 修改任意用户积分。
 * 密钥由部署环境注入（compose 统一 INNER_SECRET）；本地开发两侧默认 dev-inner-key。
 */
public class InnerKeyFilter extends OncePerRequestFilter {
    private final String expected;

    public InnerKeyFilter(String expected) {
        this.expected = expected;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getRequestURI().startsWith("/inner/")) {
            String key = req.getHeader(Const.H_INNER_KEY);
            if (key == null || !key.equals(expected)) {
                res.setStatus(401);
                res.setContentType("application/json;charset=UTF-8");
                res.getOutputStream().write(
                        "{\"code\":401,\"message\":\"内部接口未授权\",\"data\":null}".getBytes(StandardCharsets.UTF_8));
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
