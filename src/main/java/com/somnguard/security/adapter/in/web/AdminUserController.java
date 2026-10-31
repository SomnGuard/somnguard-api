package com.somnguard.security.adapter.in.web;

import com.somnguard.platform.security.RequireFeature;
import com.somnguard.security.adapter.in.web.dto.AdminUserResponse;
import com.somnguard.security.adapter.in.web.dto.CreateAdminUserRequest;
import com.somnguard.security.adapter.in.web.dto.UpdateAdminUserRequest;
import com.somnguard.security.application.service.AdminUserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @RequireFeature("user.read")
    public List<AdminUserResponse> listUsers() {
        return adminUserService.listUsers();
    }

    @GetMapping("/{id}")
    @RequireFeature("user.read")
    public AdminUserResponse getUser(@PathVariable UUID id) {
        return adminUserService.getUser(id);
    }

    @PostMapping
    @RequireFeature("user.write")
    public ResponseEntity<AdminUserResponse> createUser(@Valid @RequestBody CreateAdminUserRequest request,
            Authentication authentication) {
        AdminUserResponse created = adminUserService.createUser(request, extractUserId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @RequireFeature("user.write")
    public AdminUserResponse updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateAdminUserRequest request,
            Authentication authentication) {
        return adminUserService.updateUser(id, request, extractUserId(authentication));
    }

    @DeleteMapping("/{id}")
    @RequireFeature("user.write")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id, Authentication authentication) {
        adminUserService.deleteUser(id, extractUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getSubject());
        }
        throw new IllegalArgumentException("No autenticado");
    }
}
