package com.phucle.ecommerce.configuration;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiProblem> invalidInput(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ApiProblem(
                        "about:blank",
                        "Bad Request",
                        HttpStatus.BAD_REQUEST.value(),
                        "INVALID_REQUEST",
                        "The request is invalid",
                        null,
                        Map.of()));
    }
}
