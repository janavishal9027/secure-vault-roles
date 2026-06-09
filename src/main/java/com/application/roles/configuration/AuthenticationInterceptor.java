package com.application.roles.configuration;

import com.application.roles.dtos.TokenIntrospectionResponse;
import com.application.roles.feignService.AuthenticationClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;
import java.util.List;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private final AuthenticationClient authenticationClient;

    public AuthenticationInterceptor(@Lazy AuthenticationClient authenticationClient) {
        this.authenticationClient = authenticationClient;
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

        TokenIntrospectionResponse introspection = authenticationClient.introspect(auth);
        if (introspection == null || !introspection.isActive()) {
            writeJson(response, HttpStatus.UNAUTHORIZED.value(), "Invalid or expired token");
            return false;
        }

        List<String> roles = introspection.getRoles();
        if (roles == null || !roles.contains("ROLE_ADMIN")) {
            writeJson(response, HttpStatus.FORBIDDEN.value(), "Admin role required");
            return false;
        }

        // Optional: attach user info for audit logs
        request.setAttribute("auth_username", introspection.getUsername());

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
