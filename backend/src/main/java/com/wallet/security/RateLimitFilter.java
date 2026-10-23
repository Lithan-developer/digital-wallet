package com.wallet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/** Fixed-window rate limiter backed by Redis, shared across all app instances. Also tags each request with a requestId for the audit trail. */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;
    @Value("${app.rate-limit.auth-per-minute}") private int authLimit;
    @Value("${app.rate-limit.api-per-minute}") private int apiLimit;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);
        res.setHeader("X-Request-Id", requestId);
        try {
            boolean auth = req.getRequestURI().startsWith("/api/auth");
            int limit = auth ? authLimit : apiLimit;
            String key = "rl:" + (auth ? "auth:" : "api:") + req.getRemoteAddr() + ":" + (System.currentTimeMillis() / 60_000);
            try {
                Long count = redis.opsForValue().increment(key);
                if (count != null && count == 1) redis.expire(key, Duration.ofSeconds(70));
                if (count != null && count > limit) {
                    res.setStatus(429);
                    res.setHeader("Retry-After", "60");
                    res.setContentType("application/json");
                    res.getWriter().write("{\"status\":429,\"message\":\"Too many requests. Try again in a minute.\"}");
                    return;
                }
            } catch (Exception e) {
                log.warn("Rate limiter unavailable, failing open: {}", e.getMessage());
            }
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
