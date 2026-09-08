package com.somnguard.platform.config;

import com.somnguard.platform.security.RequireFeatureInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequireFeatureInterceptor requireFeatureInterceptor;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:4200,http://localhost:8080}")
    private String allowedOrigins;

    public WebMvcConfig(RequireFeatureInterceptor requireFeatureInterceptor) {
        this.requireFeatureInterceptor = requireFeatureInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requireFeatureInterceptor).addPathPatterns("/api/v1/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Location")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
