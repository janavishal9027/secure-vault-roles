package com.application.roles.controllers;

import com.application.roles.model.Roles;
import com.application.roles.model.UserRoleMapping;
import com.application.roles.repository.RoleRepository;
import com.application.roles.repository.UserRoleMappingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/user/public/userRoleMappings")
public class PublicUserRoleMappingController {

    private final UserRoleMappingRepository userRoleMappingRepository;
    private final RoleRepository roleRepository;

    public PublicUserRoleMappingController(UserRoleMappingRepository userRoleMappingRepository,
                                           RoleRepository roleRepository) {
        this.userRoleMappingRepository = userRoleMappingRepository;
        this.roleRepository = roleRepository;
    }

    @PostMapping("/default")
    public ResponseEntity<?> assignDefaultRole(@RequestParam String userId) {

        Roles role = roleRepository.findRolesByRoleType("ROLE_CUSTOMER");
        if (role == null) throw new RuntimeException("ROLE_CUSTOMER not found in roles table");

        // prevent duplicate mapping (optional)
        boolean exists = userRoleMappingRepository.existsByUserIdAndRoleId(userId, role.getRoleId());
        if (!exists) {
            LocalDateTime now = LocalDateTime.now();
            String mapId = "MAP" + now.getYear() + now.getDayOfYear() + now.getSecond() + now.getNano();

            UserRoleMapping map = UserRoleMapping.builder()
                    .mapId(mapId)
                    .userId(userId)
                    .roleId(role.getRoleId())
                    .build();

            userRoleMappingRepository.save(map);
        }

        return ResponseEntity.ok("Default ROLE_CUSTOMER assigned");
    }

}
