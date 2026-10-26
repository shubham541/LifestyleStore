package com.shubham.lifestylestore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window, in-memory rate limiter for the credential endpoints (per client IP) and the AI assistant
 * (per user). Good enough for a single instance; swap for Redis/Bucket4j when scaling horizontally.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private record Window(long start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int authLimit;
    private final int assistantLimit;

    public RateLimitFilter(int authLimit, int assistantLimit) {
        this.authLimit = authLimit;
        this.assistantLimit = assistantLimit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String key = null;
        int limit = 0;
        if (path.startsWith("/api/auth/") || path.equals("/api/account/password")) {
            key = "auth:" + request.getRemoteAddr();
            limit = authLimit;
        } else if (path.startsWith("/api/assistant")) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            key = "ai:" + (auth != null && auth.isAuthenticated() ? auth.getName() : request.getRemoteAddr());
            limit = assistantLimit;
        }

        if (key != null && !allow(key, limit)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Too many requests\",\"status\":429}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(w -> now - w.start() > WINDOW_MS);
        }
        Window updated = windows.merge(key, new Window(now, 1),
                (old, fresh) -> now - old.start() > WINDOW_MS ? fresh : new Window(old.start(), old.count() + 1));
        return updated.count() <= limit;
    }
}
