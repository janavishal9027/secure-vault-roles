package com.application.roles.configuration;

import com.application.roles.model.Roles;
import com.application.roles.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class RoleSeeder {

    private final RoleRepository roleRepository;

    public RoleSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Bean
    public CommandLineRunner seedRoles() {
        return args -> {
            createIfMissing("ROLE_CUSTOMER",
                    "Default role assigned to every new user. Allows access to basic user features such as login, profile management, and two-factor authentication operations.");

            createIfMissing("ROLE_ADMIN",
                    "Administrative role with elevated privileges. Allows management of system resources, users, and access to all admin-level endpoints.");

            createIfMissing("ROLE_DELEGATE",
                    "Approval authority role responsible for reviewing and approving or rejecting role upgrade requests from users (e.g., customer requesting admin privileges).");
        };
    }

    private void createIfMissing(String roleType, String description) {
        Roles existing = roleRepository.findRolesByRoleType(roleType);
        if (existing != null) return;

        LocalDateTime now = LocalDateTime.now();

        Roles r = Roles.builder()
                    .roleId("ROLE" + now.getYear() + now.getDayOfYear() + now.getSecond() + now.getNano())
                    .roleType(roleType)
                    .description(description)
                    .status("SUCCESS")
                    .createdAt(now)
                    .build();
            roleRepository.save(r);
    }
}
