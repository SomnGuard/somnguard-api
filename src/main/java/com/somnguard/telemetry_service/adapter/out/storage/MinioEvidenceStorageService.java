package com.somnguard.telemetry_service.adapter.out.storage;

import com.somnguard.telemetry_service.domain.exception.EvidenceStorageException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementación MinIO de {@link EvidenceStoragePort} (HU-API-007 AC-004).
 * Crea el bucket al arrancar si no existe (idempotente).
 */
@Service
public class MinioEvidenceStorageService implements EvidenceStoragePort {

    private static final Logger LOG = LoggerFactory.getLogger(MinioEvidenceStorageService.class);

    private final StorageProperties properties;
    private final MinioClient client;

    public MinioEvidenceStorageService(StorageProperties properties) {
        this.properties = properties;
        this.client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                LOG.info("Bucket MinIO creado: {}", properties.getBucket());
            }
        } catch (Exception ex) {
            LOG.warn("MinIO no disponible en {} (se reintentará en cada upload): {}",
                    properties.getEndpoint(), ex.toString());
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new EvidenceStorageException("No se pudo subir evidencia a MinIO (" + key + ")", ex);
        }
    }

    @Override
    public String bucket() {
        return properties.getBucket();
    }
}
