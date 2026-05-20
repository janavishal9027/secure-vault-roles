package com.application.roles.dtos;

import lombok.Data;
import java.util.List;

@Data
public class TokenIntrospectionResponse {
    private boolean active;
    private String username;
    private List<String> roles;
}
