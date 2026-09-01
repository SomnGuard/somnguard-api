package com.somnguard.platform.config;

import com.somnguard.platform.security.ApiKeyAuthenticationFilter;

import java.io.BufferedReader;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.jwt.public-key-location:classpath:keys/dev/public.pem}")
    private Resource publicKeyResource;

    @Value("${app.api-key.header-name:X-API-Key}")
    private String apiKeyHeader;

    @Value("${app.api-key.device-id-header:X-Device-ID}")
    private String deviceIdHeader;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/actuator/health", "/actuator/info", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
                        "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout", "/.well-known/jwks.json").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder()).jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .addFilterBefore(apiKeyFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            RSAPublicKey publicKey = loadPublicKey();
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar la clave pública JWT", e);
        }
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Bean
    public ApiKeyAuthenticationFilter apiKeyFilter() {
        return new ApiKeyAuthenticationFilter(apiKeyHeader, deviceIdHeader);
    }

    private RSAPublicKey loadPublicKey() throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        publicKeyResource.getInputStream(),
                        StandardCharsets.UTF_8))) {

            String pem = reader.lines()
                    .filter(line -> !line.startsWith("-----"))
                    .reduce("", String::concat);

            byte[] decoded = java.util.Base64.getDecoder().decode(pem);
            var spec = new java.security.spec.X509EncodedKeySpec(decoded);

            return (RSAPublicKey) java.security.KeyFactory
                    .getInstance("RSA")
                    .generatePublic(spec);
        }
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        RSAPublicKey publicKey = loadPublicKey();
        RSAKey rsaKey = new RSAKey.Builder(publicKey).keyID("somnguard-key-1").build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }
}