package com.somnguard.security.adapter.in.web;

import com.somnguard.security.adapter.in.web.dto.LoginRequest;
import com.somnguard.security.adapter.in.web.dto.LoginResponse;
import com.somnguard.security.adapter.in.web.dto.LogoutRequest;
import com.somnguard.security.adapter.in.web.dto.RefreshRequest;
import com.somnguard.security.adapter.in.web.dto.RegisterRequest;
import com.somnguard.security.adapter.in.web.dto.RegisterResponse;
import com.somnguard.security.adapter.in.web.dto.VerifyEmailRequest;
import com.somnguard.security.adapter.in.web.dto.VerifyPasswordRequest;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.application.port.in.AuthUseCase;
import com.somnguard.security.application.port.in.RegisterUserUseCase;
import com.somnguard.security.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import com.somnguard.security.application.service.EmailVerificationService;
import com.somnguard.security.domain.exception.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(RegisterUserUseCase registerUserUseCase, AuthUseCase authUseCase, EmailVerificationService emailVerificationService, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.registerUserUseCase = registerUserUseCase;
        this.authUseCase = authUseCase;
        this.emailVerificationService = emailVerificationService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

    @PostMapping("/verify-password")
    public ResponseEntity<Map<String, String>> verifyPassword(Authentication auth, @Valid @RequestBody VerifyPasswordRequest request) {
        UUID userId = extractUserId(auth);
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null || Boolean.FALSE.equals(user.getIsActive())) {
            throw new InvalidCredentialsException("Cuenta no disponible");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }
        return ResponseEntity.ok(Map.of("message", "Contraseña verificada"));
    }

    private UUID extractUserId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getSubject());
        }
        throw new IllegalArgumentException("No autenticado");
    }
}
