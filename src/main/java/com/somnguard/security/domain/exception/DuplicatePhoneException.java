package com.somnguard.security.domain.exception;

public class DuplicatePhoneException extends RuntimeException {
    public DuplicatePhoneException(String phone) {
        super("Phone already exists: " + phone);
    }
}
