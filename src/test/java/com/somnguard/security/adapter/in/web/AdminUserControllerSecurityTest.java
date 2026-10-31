package com.somnguard.security.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.somnguard.platform.security.FeatureAccessDeniedException;
import com.somnguard.platform.security.RequireFeature;
import com.somnguard.platform.security.RequireFeatureInterceptor;
import java.lang.reflect.Method;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AdminUserControllerSecurityTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void basePathIsUsersV1() {
        RequestMapping mapping = AdminUserController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/api/v1/users"}, mapping.value());
    }

    @Test
    void listAndDetailsRequireUserRead() throws Exception {
        assertFeature("listUsers", "user.read");
        assertFeature("getUser", "user.read");
        assertNotNull(AdminUserController.class.getDeclaredMethod("listUsers").getAnnotation(GetMapping.class));
    }

    @Test
    void writesRequireUserWrite() throws Exception {
        assertFeature("createUser", "user.write");
        assertFeature("updateUser", "user.write");
        assertFeature("deleteUser", "user.write");
        assertNotNull(AdminUserController.class.getDeclaredMethod("createUser",
                com.somnguard.security.adapter.in.web.dto.CreateAdminUserRequest.class,
                org.springframework.security.core.Authentication.class).getAnnotation(PostMapping.class));
        assertNotNull(find("updateUser").getAnnotation(PutMapping.class));
        assertNotNull(find("deleteUser").getAnnotation(DeleteMapping.class));
    }

    @Test
    void interceptorAuthorizesByFeaturesClaimInJwt() throws Exception {
        AdminUserController controller = new AdminUserController(null);
        HandlerMethod handler = new HandlerMethod(controller, "listUsers");
        RequireFeatureInterceptor interceptor = new RequireFeatureInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.getContext().setAuthentication(jwt("user.write"));
        assertThrows(FeatureAccessDeniedException.class,
                () -> interceptor.preHandle(request, response, handler));

        SecurityContextHolder.getContext().setAuthentication(jwt("user.read"));
        assertTrue(interceptor.preHandle(request, response, handler));
        SecurityContextHolder.clearContext();

        assertThrows(FeatureAccessDeniedException.class,
                () -> interceptor.preHandle(request, response, handler));
    }

    private JwtAuthenticationToken jwt(String feature) {
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(java.util.UUID.randomUUID().toString())
                .claim("features", java.util.List.of(feature))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new JwtAuthenticationToken(token, java.util.List.of());
    }

    private void assertFeature(String methodName, String expected) {
        RequireFeature feature = find(methodName).getAnnotation(RequireFeature.class);
        assertNotNull(feature, methodName + " debe tener autorización funcional explícita");
        assertEquals(expected, feature.value()[0]);
    }

    private Method find(String methodName) {
        return java.util.Arrays.stream(AdminUserController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst().orElseThrow();
    }
}
