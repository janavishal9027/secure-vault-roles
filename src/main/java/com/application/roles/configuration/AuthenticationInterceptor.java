package com.application.roles.configuration;

import com.application.roles.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;
import java.util.List;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    public AuthenticationInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String ctx = request.getContextPath(); // "/roles"
        if (ctx != null && !ctx.isBlank() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }

        // ✅ allow swagger/docs health endpoints
        if (path.startsWith("/swagger") || path.startsWith("/v3/api-docs") || path.startsWith("/actuator")) {
            return true;
        }

        // ✅ allow all public APIs (including signup mapping)
        if (path.startsWith("/api/public")) {
            return true;
        }

        // ✅ protect only role-mapping + role-create/delete endpoints (customize as needed)
        boolean adminOnly =
                path.startsWith("/userRoleMappings")
                        || path.startsWith("/api/role/createRoles")
                        || path.startsWith("/api/role/deleteRole");

        if (!adminOnly) return true;

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

        List<String> roles = jwtService.getRoles(token);
        if (roles == null || !roles.contains("ROLE_ADMIN")) {
            writeJson(response, HttpStatus.FORBIDDEN.value(), "Admin role required");
            return false;
        }

        // Optional: attach user info for audit logs
        request.setAttribute("auth_username", jwtService.getUsername(token));

        return true;
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json");
        try (PrintWriter out = response.getWriter()) {
            out.write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
        }
    }
}
