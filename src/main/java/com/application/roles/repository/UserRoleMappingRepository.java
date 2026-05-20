package com.application.roles.repository;

import com.application.roles.model.UserRoleMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleMappingRepository extends JpaRepository<UserRoleMapping, String> {

    List<UserRoleMapping> findUserRoleMappingByUserId(String userId);

    void deleteByRoleId(String roleId);

    boolean existsByUserIdAndRoleId(String userId, String roleId);

    long countByRoleId(String roleId);
}
