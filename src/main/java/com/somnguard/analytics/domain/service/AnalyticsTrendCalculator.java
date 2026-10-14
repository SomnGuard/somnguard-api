package com.somnguard.analytics.domain.service;

/**
 * Cálculo puro de tendencia entre dos periodos comparables.
 * Sin dependencias de infraestructura: unit-testeable.
 */
public final class AnalyticsTrendCalculator {

    private AnalyticsTrendCalculator() {
    }

    /**
     * @param previousTotal total del periodo anterior comparable
     * @param currentTotal total del periodo consultado
     * @return tendencia con delta absoluto y variación porcentual
     *         ({@code null} cuando el periodo anterior es cero)
     */
    public static Trend trend(long previousTotal, long currentTotal) {
        long delta = currentTotal - previousTotal;
        Double percentChange = null;
        if (previousTotal > 0) {
            percentChange = Math.round((delta * 1000.0) / previousTotal) / 10.0;
        }
        return new Trend(previousTotal, currentTotal, delta, percentChange);
    }

    public record Trend(long previousTotal, long currentTotal, long delta, Double percentChange) {
    }
}
