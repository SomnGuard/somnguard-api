package com.somnguard.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final String apiKeyHeader;
    private final String deviceIdHeader;

    public ApiKeyAuthenticationFilter(
            String apiKeyHeader,
            String deviceIdHeader) {
        this.apiKeyHeader = apiKeyHeader;
        this.deviceIdHeader = deviceIdHeader;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(apiKeyHeader);
        String deviceId = request.getHeader(deviceIdHeader);

        if (apiKey != null && deviceId != null) {
            try {
                UUID uuid = UUID.fromString(deviceId);

                DevicePrincipal principal =
                        new DevicePrincipal(uuid);

                Authentication authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(
                                        new SimpleGrantedAuthority("ROLE_DEVICE")
                                )
                        );

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);

            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    public record DevicePrincipal(UUID deviceId) {}
}