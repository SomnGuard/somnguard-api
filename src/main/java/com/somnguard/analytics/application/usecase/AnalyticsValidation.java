package com.somnguard.analytics.application.usecase;

/**
 * Validaciones compartidas de Analytics.
 * Los errores lanzan {@link IllegalArgumentException} (400 BAD_REQUEST vía
 * {@code GlobalExceptionHandler}) para no inventar códigos de error nuevos.
 */
public final class AnalyticsValidation {

    /** Rango máximo cuando {@code from} y {@code to} vienen ambos informados. */
    public static final long MAX_RANGE_DAYS = 93L;

    /** Tamaño máximo de página, igual que {@code EventQueryService}. */
    public static final int MAX_PAGE_SIZE = 100;

    private AnalyticsValidation() {
    }

    public static void validateRange(java.time.OffsetDateTime from, java.time.OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from debe ser anterior o igual a to");
        }
    }

    /**
     * Limita el rango temporal para proteger las agregaciones de métricas.
     * La línea de tiempo (paginada) no aplica este límite.
     */
    public static void validateMaxRange(java.time.OffsetDateTime from, java.time.OffsetDateTime to) {
        validateRange(from, to);
        if (from != null && to != null
                && java.time.Duration.between(from, to).toDays() > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "rango máximo permitido: " + MAX_RANGE_DAYS + " días");
        }
    }

    public static int safePage(int page) {
        return Math.max(page, 1);
    }

    public static int safeSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }
}
