package com.application.roles.controllers;

import com.application.roles.service.RoleService;
import com.application.roles.service.UserRoleMappingService;
import com.application.roles.utils.InternalKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/role-mapping")
public class UserRoleMappingController {

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRoleMappingService userRoleMappingService;

    // Read from config, never inlined. The previous literal was published in
    // the repository, which turned this endpoint — the one that grants
    // ROLE_ADMIN — into anonymous privilege escalation for anyone who read it.
    @Value("${internal.role-service-key}")
    private String internalKey;

    @PostMapping("/assign")
    public void createUserRoleMapping(
            @RequestHeader("X-INTERNAL-KEY") String requestKey,
            @RequestParam String roleType,
            @RequestParam String userId) {

        InternalKeys.require(internalKey, requestKey);

        String normalized = roleType.toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }

        userRoleMappingService.assignRoleToUser(normalized, userId);
    }

}
