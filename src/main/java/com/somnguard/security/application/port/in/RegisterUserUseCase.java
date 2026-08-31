package com.somnguard.security.application.port.in;

import java.util.UUID;

public interface RegisterUserUseCase {

    UUID register(RegisterUserCommand command);

    record RegisterUserCommand(
            String email,
            String password,
            String firstName,
            String lastName,
            String phone
    ) {}
}
