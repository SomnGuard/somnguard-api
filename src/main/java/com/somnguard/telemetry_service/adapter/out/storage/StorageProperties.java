package com.somnguard.telemetry_service.adapter.out.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuración S3/MinIO (HU-API-007 AC-004, ADR-006).
 * MinIO es un object storage S3-compatible que corre en Docker como MailHog
 * (ver {@code docker-compose.yml} servicio {@code minio}).
 */
@Component
@ConfigurationProperties(prefix = "app.storage.s3")
public class StorageProperties {

    private String endpoint = "http://localhost:9000";
    private String region = "us-east-1";
    private String bucket = "somnguard-evidence";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
}
