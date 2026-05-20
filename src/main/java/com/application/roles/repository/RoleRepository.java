package com.application.roles.repository;

import com.application.roles.model.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Roles, String> {
    Roles findRolesByRoleId(String roleId);

    Roles findRolesByRoleType(String roleType);
}
