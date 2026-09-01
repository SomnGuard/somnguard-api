package com.somnguard.platform.config;

import com.somnguard.platform.security.RequireFeatureInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequireFeatureInterceptor requireFeatureInterceptor;

    public WebMvcConfig(RequireFeatureInterceptor requireFeatureInterceptor) {
        this.requireFeatureInterceptor = requireFeatureInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requireFeatureInterceptor).addPathPatterns("/api/v1/**");
    }
}
