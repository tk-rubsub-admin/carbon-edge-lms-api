package com.carbonedge.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** In-memory IP limiter. For multiple backend replicas, apply the equivalent limit at the gateway. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Policy policy = policyFor(request.getMethod(), request.getServletPath());
        if (policy != null && !allow(clientIp(request) + ':' + request.getServletPath(), policy)) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(policy.windowSeconds));
            response.setContentType("application/json");
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "code", "RATE_LIMITED",
                    "message", "Too many requests. Please try again later.",
                    "timestamp", Instant.now().toString(),
                    "validationErrors", Map.of()
            ));
            return;
        }
        chain.doFilter(request, response);
    }

    private Policy policyFor(String method, String path) {
        if ("POST".equals(method) && "/auth/login".equals(path)) return new Policy(5, 60);
        if ("POST".equals(method) && "/auth/forgot-password".equals(path)) return new Policy(3, 900);
        if ("POST".equals(method) && "/auth/register".equals(path)) return new Policy(5, 3600);
        if ("POST".equals(method) && "/auth/refresh".equals(path)) return new Policy(30, 60);
        if (!"GET".equals(method) && !"HEAD".equals(method) && path.startsWith("/")) return new Policy(120, 60);
        return null;
    }

    private boolean allow(String key, Policy policy) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt >= policy.windowSeconds * 1000L) {
                return new Window(now, 1);
            }
            current.requests++;
            return current;
        });
        return window.requests <= policy.limit;
    }

    private String clientIp(HttpServletRequest request) { return request.getRemoteAddr(); }

    private record Policy(int limit, long windowSeconds) { }
    private static final class Window {
        private final long startedAt;
        private int requests;
        private Window(long startedAt, int requests) { this.startedAt = startedAt; this.requests = requests; }
    }
}
