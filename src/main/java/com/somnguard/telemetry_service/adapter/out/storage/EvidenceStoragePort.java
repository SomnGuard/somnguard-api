package com.somnguard.telemetry_service.adapter.out.storage;

/**
 * Puerto de salida a object storage (bucket {@code somnguard-evidence}).
 * Solo bytes; los metadatos viven en Postgres (ADR-006).
 */
public interface EvidenceStoragePort {

    void put(String key, byte[] content, String contentType);

    String bucket();
}
