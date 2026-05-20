package com.application.roles.service;

import com.application.roles.dtos.RoleDto;
import com.application.roles.dtos.RoleRespDto;
import com.application.roles.model.Roles;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface RoleService {

    Roles createRole(RoleDto roleDto, String username);

    List<Roles> listOfRoles();

    void deleteRole(String roleId);

    List<RoleRespDto> getRolesByUserId(String userId);

    List<String> getRolesByUsername(String username);

    Roles getRolesByRoleType(String roleType);

}
