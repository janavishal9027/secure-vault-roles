package com.application.roles.controllers;

import com.application.roles.service.RoleService;
import com.application.roles.service.UserRoleMappingService;
import com.application.roles.utils.ApiResponse;
import com.application.roles.utils.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/roles")
public class InternalRolesController {

    @Autowired
    private UserRoleMappingService userRoleMappingService;

    @Value("${internal.role-service-key}")
    private String internalKey;

    @PostMapping("/delegate-role")
    public ResponseEntity<ApiResponse> assignDelegateRole(
            @RequestHeader("X-INTERNAL-KEY") String requestKey,
            @RequestParam String roleType,
            @RequestParam String userId
    ) {
        if (!internalKey.equals(requestKey)) {
            throw new RuntimeException("Unauthorized internal request");
        }

        String normalized = roleType.toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }

        if (!"ROLE_DELEGATE".equals(normalized)) {
            throw new RuntimeException("Only delegate assignment allowed here");
        }

        userRoleMappingService.assignRoleToUser(normalized, userId);
        return new ResponseEntity<>(
                new ApiResponse(
                        Constants.SUCCESS.name(),
                        "Role assigned successfully! User " + userId + " assigned role " + normalized
                ), HttpStatus.OK
        );
    }

    @GetMapping("/delegates/count")
    public ResponseEntity<Long> countDelegates(
            @RequestHeader("X-INTERNAL-KEY") String requestKey
    ) {
        if (!internalKey.equals(requestKey)) {
            throw new RuntimeException("Unauthorized internal request");
        }

        return ResponseEntity.ok(userRoleMappingService.countUsersByRole("ROLE_DELEGATE"));
    }
}
