package com.application.roles.configuration;

import com.application.roles.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates inbound requests against the JWT minted by the Authentication
 * service.
 *
 * The rule is deny-by-default: anything not named in {@link #PUBLIC_PATHS} or
 * {@link #ADMIN_PATHS} requires a valid token. The previous version inverted
 * this — it listed a handful of admin paths and waved everything else through,
 * which left the whole role catalogue and every user→role lookup readable by
 * anonymous callers, and left /api/role-mapping/assign guarded by nothing but a
 * shared key that was hardcoded in source.
 */
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    /** Reachable without a token. */
    private static final String[] PUBLIC_PATHS = {
            "/swagger",
            "/v3/api-docs",
            "/actuator",
            // Signup assigns the default role before the user has a token.
            "/api/public",
            "/api/user/public/userRoleMappings",
            // Service-to-service; these verify the shared internal key themselves.
            "/api/internal/",
            "/api/role-mapping/",
    };

    /** Requires a valid token *and* ROLE_ADMIN. */
    private static final String[] ADMIN_PATHS = {
            "/userRoleMappings",
            "/api/role/createRoles",
            "/api/role/deleteRole",
    };

    private final JwtService jwtService;

    /**
     * Shared secret presented by sibling services. Authentication has to read
     * a user's roles during signup and login — before that user has a token —
     * so a valid internal key stands in for a bearer token on those calls.
     */
    @Value("${internal.role-service-key}")
    private String internalKey;

    public AuthenticationInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        String ctx = request.getContextPath(); // "/roles"
        if (ctx != null && !ctx.isBlank() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }

        if (matches(path, PUBLIC_PATHS)) {
            return true;
        }

        // A sibling service authenticating with the shared key. The calling
        // service has already authorized its own user (or is acting on the
        // signup path, where no user token exists yet).
        if (hasValidInternalKey(request)) {
            return true;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeJson(response, HttpStatus.UNAUTHORIZED.value(), "Missing or invalid Authorization header");
            return false;
        }

        // Verify the JWT locally using the shared secret — no call back into
        // the Authentication service. JWT_SECRET_KEY must match the secret
        // Authentication signs with.
        String token = auth.substring(7);
        if (!jwtService.isTokenValid(token)) {
            writeJson(response, HttpStatus.UNAUTHORIZED.value(), "Invalid or expired token");
            return false;
        }

        if (matches(path, ADMIN_PATHS)) {
            List<String> roles = jwtService.getRoles(token);
            if (roles == null || !roles.contains("ROLE_ADMIN")) {
                writeJson(response, HttpStatus.FORBIDDEN.value(), "Admin role required");
                return false;
            }
        }

        // Attach user info for audit logs
        request.setAttribute("auth_username", jwtService.getUsername(token));

        return true;
    }

    /**
     * Constant-time check of the X-INTERNAL-KEY header. A blank configured key
     * never matches, so a missing secret cannot silently open the service up.
     */
    private boolean hasValidInternalKey(HttpServletRequest request) {
        if (internalKey == null || internalKey.isBlank()) {
            return false;
        }
        String presented = request.getHeader("X-INTERNAL-KEY");
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                internalKey.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private boolean matches(String path, String[] prefixes) {
        for (String prefix : prefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json");
        try (PrintWriter out = response.getWriter()) {
            out.write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
        }
    }
}
