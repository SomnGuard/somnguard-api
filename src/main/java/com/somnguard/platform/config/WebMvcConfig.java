package com.somnguard.platform.config;

import com.somnguard.platform.logging.TraceIdFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TraceIdFilter traceIdFilter;

    public WebMvcConfig(TraceIdFilter traceIdFilter) {
        this.traceIdFilter = traceIdFilter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                    jakarta.servlet.http.HttpServletResponse response,
                                    Object handler) {
                traceIdFilter.doFilterInternal(request, response, () -> true);
                return true;
            }
        });
    }
}