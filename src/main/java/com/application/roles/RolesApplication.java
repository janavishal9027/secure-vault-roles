package com.application.roles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class RolesApplication {

    public static void main(String[] args) {
        SpringApplication.run(RolesApplication.class, args);
    }

}
