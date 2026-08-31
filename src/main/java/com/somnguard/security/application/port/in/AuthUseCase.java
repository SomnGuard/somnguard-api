package com.somnguard.security.application.port.in;

import com.somnguard.security.adapter.in.web.dto.LoginResponse;

public interface AuthUseCase {

    LoginResponse login(String email, String password, String ip, String userAgent);

    LoginResponse refresh(String refreshToken);

    void logout(String refreshToken);
}
