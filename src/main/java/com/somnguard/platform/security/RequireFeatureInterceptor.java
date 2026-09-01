package com.somnguard.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RequireFeatureInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) return true;
        RequireFeature ann = hm.getMethodAnnotation(RequireFeature.class);
        if (ann == null) ann = hm.getBeanType().getAnnotation(RequireFeature.class);
        if (ann == null) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new FeatureAccessDeniedException(ann.value()[0]);
        }
        List<String> required = List.of(ann.value());
        List<String> features = List.of();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Object claim = jwtAuth.getToken().getClaim("features");
            if (claim instanceof List<?> list) {
                features = list.stream().map(Object::toString).toList();
            }
        } else {
            features = auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        }
        boolean ok = required.stream().anyMatch(features::contains);
        if (!ok) {
            throw new FeatureAccessDeniedException(String.join(",", required));
        }
        return true;
    }
}
