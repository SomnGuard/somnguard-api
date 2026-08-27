package com.somnguard.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${springdoc.api-docs.path:/v3/api-docs}")
    private String apiDocsPath;

    @Value("${springdoc.swagger-ui.path:/swagger-ui.html}")
    private String swaggerUiPath;

    @Bean
    public OpenAPI customOpenAPI(
            @Value("${spring.application.name:somnguard-api}") String applicationName,
            @Value("${app.version:0.0.1-SNAPSHOT}") String appVersion
    ) {
        final String securitySchemeName = "bearerAuth";
        final String apiKeySchemeName = "apiKeyAuth";

        return new OpenAPI()
                .info(new Info()
                        .title(applicationName)
                        .version(appVersion)
                        .description("SomnGuard API — Sistema de monitoreo de somnolencia para conductores")
                        .license(new License()
                                .name("Proprietary")
                                .url("https://somnguard.com/license")))
                .servers(List.of(
                        new Server().url("/").description("Default Server"),
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("https://api.somnguard.com").description("Production")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .addSecurityItem(new SecurityRequirement().addList(apiKeySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT RS256 token para usuarios humanos (Portal/App)"))
                        .addSecuritySchemes(apiKeySchemeName,
                                new SecurityScheme()
                                        .name(apiKeySchemeName)
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-API-Key")
                                        .description("API Key para dispositivos edge (Raspberry Pi). Requiere header X-Device-ID)")));
    }
}