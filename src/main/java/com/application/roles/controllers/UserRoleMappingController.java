package com.application.roles.controllers;

import com.application.roles.service.RoleService;
import com.application.roles.service.UserRoleMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/role-mapping")
public class UserRoleMappingController {

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRoleMappingService userRoleMappingService;

    @PostMapping("/assign")
    public void createUserRoleMapping(
            @RequestHeader("X-INTERNAL-KEY") String internalKey,
            @RequestParam String roleType,
            @RequestParam String userId) {

        if (!internalKey.equals("MY_SUPER_SECRET_KEY")) {
            throw new RuntimeException("Unauthorized internal call");
        }

        String normalized = roleType.toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }

        userRoleMappingService.assignRoleToUser(normalized, userId);
    }

}
