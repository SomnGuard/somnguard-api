package com.somnguard.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final int maxPerMinute;
    private final ConcurrentHashMap<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.auth-requests-per-minute:5}") int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/auth/")) {
            String ip = request.getRemoteAddr();
            Deque<Instant> deque = buckets.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
            Instant now = Instant.now();
            Instant windowStart = now.minusSeconds(60);
            while (!deque.isEmpty() && deque.peekFirst().isBefore(windowStart)) {
                deque.pollFirst();
            }
            if (deque.size() >= maxPerMinute) {
                response.setStatus(429);
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"type\":\"https://somnguard.com/errors/rate-limit\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit 5 req/min exceeded\"}");
                return;
            }
            deque.addLast(now);
        }
        filterChain.doFilter(request, response);
    }
}
