package com.somnguard.platform.security;

public class FeatureAccessDeniedException extends RuntimeException {
    public FeatureAccessDeniedException(String feature) {
        super("Feature " + feature + " required");
    }
}
