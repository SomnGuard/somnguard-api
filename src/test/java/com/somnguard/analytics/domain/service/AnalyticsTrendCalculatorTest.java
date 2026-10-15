package com.somnguard.analytics.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AnalyticsTrendCalculatorTest {

    @Test
    void trendComputesDeltaAndPercent() {
        AnalyticsTrendCalculator.Trend trend = AnalyticsTrendCalculator.trend(100L, 150L);
        assertEquals(100L, trend.previousTotal());
        assertEquals(150L, trend.currentTotal());
        assertEquals(50L, trend.delta());
        assertEquals(50.0, trend.percentChange());
    }

    @Test
    void trendIsNullPercentWhenPreviousIsZero() {
        AnalyticsTrendCalculator.Trend trend = AnalyticsTrendCalculator.trend(0L, 10L);
        assertEquals(10L, trend.delta());
        assertNull(trend.percentChange());
    }

    @Test
    void trendSupportsNegativeDelta() {
        AnalyticsTrendCalculator.Trend trend = AnalyticsTrendCalculator.trend(200L, 150L);
        assertEquals(-50L, trend.delta());
        assertEquals(-25.0, trend.percentChange());
    }
}
