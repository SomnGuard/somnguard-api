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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
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

    @Value("${app.auth.refresh-cookie.name:somnguard_refresh}")
    private String refreshCookieName;

    @Value("${app.auth.refresh-cookie.secure:true}")
    private boolean refreshCookieSecure;

    @Value("${app.jwt.refresh-token-expiry-days:7}")
    private long refreshTokenExpiryDays;

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
        return loginResponse(resp, http);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody(required = false) RefreshRequest request, HttpServletRequest http) {
        LoginResponse resp = authUseCase.refresh(resolveRefreshToken(request == null ? null : request.refreshToken(), http));
        return loginResponse(resp, http);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request, HttpServletRequest http) {
        String refreshToken = request == null ? null : request.refreshToken();
        if (StringUtils.hasText(refreshToken) || findRefreshCookie(http) != null) {
            authUseCase.logout(resolveRefreshToken(refreshToken, http));
        }
        ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
        if (isBrowserRequest(http)) {
            response.header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString());
        }
        return response.build();
    }

    private ResponseEntity<LoginResponse> loginResponse(LoginResponse response, HttpServletRequest request) {
        if (!isBrowserRequest(request)) {
            return ResponseEntity.ok(response);
        }
        LoginResponse body = new LoginResponse(response.accessToken(), null, response.tokenType(), response.expiresIn());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response.refreshToken()).toString())
                .body(body);
    }

    private boolean isBrowserRequest(HttpServletRequest request) {
        return StringUtils.hasText(request.getHeader(HttpHeaders.ORIGIN));
    }

    private String resolveRefreshToken(String bodyToken, HttpServletRequest request) {
        if (StringUtils.hasText(bodyToken)) return bodyToken;
        String cookieToken = findRefreshCookie(request);
        if (StringUtils.hasText(cookieToken)) return cookieToken;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token required");
    }

    private String findRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (refreshCookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(refreshCookieName, token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(refreshTokenExpiryDays))
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
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
