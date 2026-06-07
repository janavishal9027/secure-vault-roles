package com.application.roles.feignService;

import com.application.roles.model.Users;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "Authentication", url = "${digital.app.authentication}", path = "/api/user")
public interface AuthenticationClient {

    @GetMapping("/getUserByUsername")
    Users getUserByUsername(@RequestParam String username);

}
