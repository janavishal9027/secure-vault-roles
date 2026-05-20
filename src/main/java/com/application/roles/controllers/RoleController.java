package com.application.roles.controllers;

import com.application.roles.dtos.RoleDto;
import com.application.roles.dtos.RoleRespDto;
import com.application.roles.model.Roles;
import com.application.roles.service.RoleService;
import com.application.roles.utils.ApiResponse;
import com.application.roles.utils.Constants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/role")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @PostMapping("/createRoles")
    public ResponseEntity<ApiResponse> createRoles(@RequestBody RoleDto role, HttpServletRequest request) {
        String username = (String) request.getAttribute("auth_username");
        return new ResponseEntity<>(
                new ApiResponse(Constants.SUCCESS.name(),
                        roleService.createRole(role, username),
                        "Role creation is successful"),
                HttpStatus.CREATED
        );
    }

    @GetMapping("/getAllRoles")
    public List<Roles> getAllRoles(){
        return roleService.listOfRoles();
    }

    @DeleteMapping("/deleteRole")
    public ResponseEntity<ApiResponse> deleteRole(@RequestParam String roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.ok(
                new ApiResponse(
                        Constants.SUCCESS.name(),
                        "Role deleted successfully"
                )
        );
    }

    @GetMapping("/getRolesByUsername")
    public List<String> getRolesByUsername(@RequestParam String username){
        return roleService.getRolesByUsername(username);
    }

    @GetMapping("/rolesByUserId")
    public List<RoleRespDto> getRolesByUserId(@RequestParam String userId){
        return roleService.getRolesByUserId(userId);
    }

    @GetMapping("/getRolesByRoleName")
    public ResponseEntity<ApiResponse> getRolesByRoleType(String roleType){
        return new ResponseEntity<>(
                new ApiResponse(
                        Constants.SUCCESS.name(),
                        roleService.getRolesByRoleType(roleType),
                        "Role found by using roled type"
                ), HttpStatus.OK);
    }

}
