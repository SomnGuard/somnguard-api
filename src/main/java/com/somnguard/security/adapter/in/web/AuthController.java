package com.somnguard.security.adapter.in.web;

import com.somnguard.security.adapter.in.web.dto.LoginRequest;
import com.somnguard.security.adapter.in.web.dto.LoginResponse;
import com.somnguard.security.adapter.in.web.dto.LogoutRequest;
import com.somnguard.security.adapter.in.web.dto.RefreshRequest;
import com.somnguard.security.adapter.in.web.dto.RegisterRequest;
import com.somnguard.security.adapter.in.web.dto.RegisterResponse;
import com.somnguard.security.adapter.in.web.dto.VerifyEmailRequest;
import com.somnguard.security.application.port.in.AuthUseCase;
import com.somnguard.security.application.port.in.RegisterUserUseCase;
import com.somnguard.security.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import com.somnguard.security.application.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final AuthUseCase authUseCase;
    private final EmailVerificationService emailVerificationService;

    public AuthController(RegisterUserUseCase registerUserUseCase, AuthUseCase authUseCase, EmailVerificationService emailVerificationService) {
        this.registerUserUseCase = registerUserUseCase;
        this.authUseCase = authUseCase;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterUserCommand command = new RegisterUserCommand(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName(),
                request.phone()
        );
        UUID id = registerUserUseCase.register(command);
        RegisterResponse body = new RegisterResponse(id, request.email().trim().toLowerCase());
        return ResponseEntity.created(URI.create("/api/v1/auth/register/" + id)).body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        String ua = http.getHeader("User-Agent");
        LoginResponse resp = authUseCase.login(request.email(), request.password(), ip, ua);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse resp = authUseCase.refresh(request.refreshToken());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authUseCase.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.ok(Map.of("message", "Correo verificado, ya puedes iniciar sesión"));
    }
}
