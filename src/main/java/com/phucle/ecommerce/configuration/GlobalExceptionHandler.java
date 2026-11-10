package com.phucle.ecommerce.configuration;

import com.phucle.ecommerce.identity.api.InvalidCredentialsException;
import com.phucle.ecommerce.identity.api.LoginAlreadyExistsException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
class GlobalExceptionHandler {

    private final ApiProblemFactory problems;

    GlobalExceptionHandler(ApiProblemFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiProblem> invalidInput(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.create(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiProblem> invalidRequest(Exception exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (exception instanceof MethodArgumentNotValidException validation) {
            for (FieldError error : validation.getBindingResult().getFieldErrors()) {
                fields.put(error.getField(), "Invalid value");
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.create(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid", fields));
    }

    @ExceptionHandler(LoginAlreadyExistsException.class)
    ResponseEntity<ApiProblem> loginAlreadyExists() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.create(HttpStatus.CONFLICT, "LOGIN_ALREADY_EXISTS", "The login is already in use"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiProblem> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Bearer")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.create(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials"));
    }
}
