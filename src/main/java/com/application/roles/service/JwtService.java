package com.application.roles.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Verify-only JWT helper. The roles service never issues tokens; it only
 * needs to validate the JWTs minted by the Authentication service and read
 * their claims locally, so it can authorize requests without a network call
 * back into Authentication.
 *
 * The signing-key derivation (Base64-decode then HMAC-SHA) mirrors
 * Authentication's JwtService exactly, so any token Authentication signs
 * with the shared secret parses here identically.
 */
@Service
public class JwtService {

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    private SecretKey getSigningKey() {
        byte[] decoded = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(decoded);
    }

    /**
     * Parses and cryptographically verifies the token (signature + structure).
     * Throws a JJWT exception if the token is malformed, tampered with, or
     * signed with a different key.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * @return true only if the token's signature verifies and it has not expired.
     */
    public boolean isTokenValid(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            return expiration == null || expiration.after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Reads the "roles" claim. Authentication stores it as a JSON list, but
     * a comma-separated string is tolerated for robustness.
     */
    public List<String> getRoles(String token) {
        Object rolesObj = parseClaims(token).get("roles");
        if (rolesObj == null) {
            return List.of();
        }
        if (rolesObj instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        String rolesStr = String.valueOf(rolesObj);
        if (rolesStr.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rolesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
