package com.somnguard.platform.error;

public class NotFoundException extends BusinessException {

    public NotFoundException(String resource, Object id) {
        super(ErrorCode.NOT_FOUND, String.format("%s not found with id: %s", resource, id));
    }

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}