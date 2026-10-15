package com.somnguard.analytics.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.somnguard.platform.security.RequireFeature;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Verifica el contrato de seguridad y rutas del controlador sin contexto Spring:
 * prefijo, subrutas y permisos requeridos por endpoint.
 */
class AnalyticsControllerSecurityTest {

    @Test
    void basePathIsAnalyticsV1() {
        RequestMapping mapping = AnalyticsController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/api/v1/analytics"}, mapping.value());
    }

    @Test
    void timelineRequiresAnalyticsRead() throws Exception {
        assertGetPath("timeline", "/timeline");
        assertFeatures("timeline", List.of("analytics.read"));
    }

    @Test
    void metricsRequiresAnalyticsRead() throws Exception {
        assertGetPath("metrics", "/metrics");
        assertFeatures("metrics", List.of("analytics.read"));
    }

    @Test
    void summaryRequiresAnalyticsRead() throws Exception {
        assertGetPath("summary", "/summary");
        assertFeatures("summary", List.of("analytics.read"));
    }

    @Test
    void reportsRequiresAnalyticsGenerate() throws Exception {
        assertGetPath("reports", "/reports");
        assertFeatures("reports", List.of("analytics.generate"));
    }

    private void assertGetPath(String methodName, String path) throws Exception {
        Method method = findMethod(methodName);
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertNotNull(mapping, methodName + " debe ser GET");
        assertEquals(List.of(path), Arrays.asList(mapping.value()));
    }

    private void assertFeatures(String methodName, List<String> expected) throws Exception {
        RequireFeature feature = findMethod(methodName).getAnnotation(RequireFeature.class);
        assertNotNull(feature, methodName + " debe exigir permiso");
        assertEquals(expected, Arrays.asList(feature.value()));
    }

    private Method findMethod(String methodName) {
        return Arrays.stream(AnalyticsController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("método no encontrado: " + methodName));
    }
}
