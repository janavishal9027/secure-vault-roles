package com.application.roles.exceptions;


import lombok.Data;

@Data
public class AuthenticationException extends RuntimeException {
    private String message;
    public AuthenticationException(String message) {
        this.message = message;
    }
}
