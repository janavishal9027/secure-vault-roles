package com.application.roles.service;

import com.application.roles.dtos.RoleDto;
import com.application.roles.dtos.RoleRespDto;
import com.application.roles.feignService.AuthenticationClient;
import com.application.roles.model.Roles;
import com.application.roles.model.UserRoleMapping;
import com.application.roles.model.Users;
import com.application.roles.repository.RoleRepository;
import com.application.roles.repository.UserRoleMappingRepository;
import com.application.roles.utils.Constants;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RoleServiceImpl implements RoleService{

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleMappingRepository userRoleMappingRepository;

    @Autowired
    private AuthenticationClient authenticationClient;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public Roles createRole(RoleDto roleDto, String username) {
        LocalDateTime now = LocalDateTime.now();
        Users userByUsername = authenticationClient.getUserByUsername(username);
        String roleId = "ROLE" + now.getYear() + now.getDayOfYear() + now.getSecond() + now.getNano();
        String mapId = "MAP" + now.getYear() + now.getDayOfYear() + now.getSecond() + now.getNano();
        Roles role = Roles.builder()
                .roleId(roleId)
                .roleType(roleDto.getRoleType())
                .description(roleDto.getDescription())
                .createdAt(now)
                .updatedAt(null)
                .users(userByUsername)
                .status(Constants.SUCCESS.name())
                .build();
        UserRoleMapping map = UserRoleMapping.builder().mapId(mapId).roleId(roleId).userId(userByUsername.getUserId()).build();
        userRoleMappingRepository.save(map);
        return roleRepository.save(role);
    }

    @Override
    public List<Roles> listOfRoles() {
        return roleRepository.findAll();
    }

    @Override
    @Transactional
    public void deleteRole(String roleId) {
        Roles role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found with id: " + roleId));

        userRoleMappingRepository.deleteByRoleId(roleId);
        roleRepository.delete(role);
    }

    @Override
    public List<RoleRespDto> getRolesByUserId(String userId) {
        List<UserRoleMapping> userRole = userRoleMappingRepository.findUserRoleMappingByUserId(userId);
        List<RoleRespDto> roles = new ArrayList<>();
        if(userRole!=null){
            for(UserRoleMapping userRoleMapping : userRole){
                Roles rolesByRoleId = roleRepository.findRolesByRoleId(userRoleMapping.getRoleId());
                if(rolesByRoleId!=null){
                    roles.add(modelMapper.map(rolesByRoleId, RoleRespDto.class));
                }
            }
        }
        return roles;
    }

//    @Override
//    public List<String> getRolesByUserId(String userId) {
//        List<String> collect = userRoleMappingRepository.findByUserId(userId)
//                .stream()
//                .map(ur -> ur.getRole().getRoleType())
//                .collect(Collectors.toList());
//        return collect;
//    }

    @Override
    public List<String> getRolesByUsername(String username){
        Users users = authenticationClient.getUserByUsername(username);
        if(users == null){
            throw new RuntimeException("User not found with username: " + username);
        }

        List<UserRoleMapping> userRoleMapping = userRoleMappingRepository.findUserRoleMappingByUserId(users.getUserId());

        return userRoleMapping
                .stream()
                .map(ur -> {
                    Roles role = roleRepository.findRolesByRoleId(ur.getRoleId());
                    if (role == null) {
                        throw new RuntimeException("Role not found for roleId: " + ur.getRoleId());
                    }
                    return role.getRoleType();
                })
                .toList();
    }

    @Override
    public Roles getRolesByRoleType(String roleType) {
        String normalized = roleType.toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        Roles role = roleRepository.findRolesByRoleType(normalized);
        if (role == null) {
            throw new RuntimeException("Role not found with roleType: " + normalized);
        }
        return role;
    }

}
