package com.application.roles.feignService;

import com.application.roles.dtos.TokenIntrospectionResponse;
import com.application.roles.model.Users;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "Authentication", url = "${digital.app.authentication}", path = "/api/user")
public interface AuthenticationClient {

    @GetMapping("/public/validate")
    Boolean validateToken(@RequestParam String token);

    @GetMapping("/getUserByUsername")
    Users getUserByUsername(@RequestParam String username);

    @PostMapping("/public/introspect")
    TokenIntrospectionResponse introspect(
            @RequestHeader("Authorization") String authHeader
    );

    @PostMapping("/public/userRoleMappings/default")
    void assignDefaultRole(@RequestParam("userId") String userId);

}
