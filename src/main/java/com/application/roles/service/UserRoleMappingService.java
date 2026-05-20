package com.application.roles.service;

import com.application.roles.model.UserRoleMapping;

public interface UserRoleMappingService {

    void assignRoleToUser(String roleType, String userId);

    long countUsersByRole(String roleType);

}
