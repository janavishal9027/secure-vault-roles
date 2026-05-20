package com.application.roles.service;

import com.application.roles.model.Roles;
import com.application.roles.model.UserRoleMapping;
import com.application.roles.repository.RoleRepository;
import com.application.roles.repository.UserRoleMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserRoleMappingServiceImpl implements UserRoleMappingService {

    @Autowired
    private UserRoleMappingRepository userRoleMappingRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public void assignRoleToUser(String roleType, String userId) {
        String normalized = roleType.toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }

        Roles role = roleRepository.findRolesByRoleType(normalized);
        if (role == null) {
            throw new RuntimeException("Role not found: " + normalized);
        }

        boolean alreadyExists = userRoleMappingRepository.existsByUserIdAndRoleId(userId, role.getRoleId());
        if (alreadyExists) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        UserRoleMapping mapping = new UserRoleMapping();
        mapping.setMapId("MAP" + now.getYear() + now.getDayOfYear() + now.getSecond() + now.getNano());
        mapping.setUserId(userId);
        mapping.setRoleId(role.getRoleId());

        userRoleMappingRepository.save(mapping);
    }

    @Override
    public long countUsersByRole(String roleType) {
        String normalized = roleType.toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }

        Roles role = roleRepository.findRolesByRoleType(normalized);
        if (role == null) {
            throw new RuntimeException("Role not found: " + normalized);
        }

        return userRoleMappingRepository.countByRoleId(role.getRoleId());
    }
}
