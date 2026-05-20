package com.application.roles.exceptions;

import com.application.roles.utils.ApiResponse;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public ResponseEntity<ApiResponse> getResponse(String message, int statusCode){
        return new ResponseEntity<>(
                ApiResponse.builder()
                        .status("FAILED")
                        .message(message)
                        .build(),
                HttpStatusCode.valueOf(statusCode)
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse> handleAuthenticationException(AuthenticationException authenticationException){
        String msg = authenticationException.getMessage();
        try{
            String[] split = msg.split(": ");
            if(split.length > 1){
                String replace = split[1].replace("[", "").replace("]", "").replace("\\", "");
                JSONObject object = new JSONObject(replace);
                String message = object.getString("message");
                if(!message.isEmpty()){
                    return getResponse(message, HttpStatus.UNAUTHORIZED.value());
                }
            }
        } catch (Exception e){
            log.debug("Failed to parse authentication error message: {}", e.getMessage());
        }
        return getResponse(msg, HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(NoRecordsFoundException.class)
    public ResponseEntity<ApiResponse> noRecordFoundException(NoRecordsFoundException noRecordsFoundException){
        return getResponse(noRecordsFoundException.getMessage(), HttpStatus.NOT_FOUND.value());
    }
}
