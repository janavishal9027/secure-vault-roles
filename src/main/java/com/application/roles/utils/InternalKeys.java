package com.application.roles.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared-secret checks for the service-to-service endpoints.
 */
public final class InternalKeys {

    private InternalKeys() {
    }

    /**
     * Rejects the request unless the presented key matches the configured one.
     *
     * Uses a constant-time comparison: {@code String.equals} short-circuits on
     * the first differing byte, which leaks the key one character at a time to
     * an attacker who can measure response times.
     *
     * An unconfigured key is also a failure — a blank secret must never mean
     * "let everyone in".
     */
    public static void require(String configuredKey, String presentedKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new SecurityException("Internal service key is not configured");
        }
        if (presentedKey == null || !MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8),
                presentedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Unauthorized internal request");
        }
    }
}
