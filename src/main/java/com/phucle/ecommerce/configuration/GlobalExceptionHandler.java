package com.phucle.ecommerce.configuration;

import com.phucle.ecommerce.identity.api.InvalidCredentialsException;
import com.phucle.ecommerce.identity.api.LoginAlreadyExistsException;
import com.phucle.ecommerce.shared.InvalidCursorException;
import com.phucle.ecommerce.discovery.PublicProductNotFoundException;
import com.phucle.ecommerce.cart.domain.CartFailure;
import com.phucle.ecommerce.identity.application.OwnedAddressNotFoundException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class GlobalExceptionHandler {

    private final ApiProblemFactory problems;

    GlobalExceptionHandler(ApiProblemFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(CartFailure.class)
    ResponseEntity<ApiProblem> cartError(CartFailure exception) {
        HttpStatus status = switch (exception.code()) {
            case AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case ROLE_DENIED, ORIGIN_DENIED -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case STALE_VERSION, ITEM_UNAVAILABLE, QUANTITY_EXCEEDED -> HttpStatus.CONFLICT;
        };
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (status == HttpStatus.UNAUTHORIZED) response.header("WWW-Authenticate", "Bearer");
        return response
                .body(problems.create(status, exception.code().name(), "The cart request could not be completed"));
    }

    @ExceptionHandler(InvalidCursorException.class)
    ResponseEntity<ApiProblem> invalidCursor() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.create(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "The cursor is invalid"));
    }

    @ExceptionHandler(PublicProductNotFoundException.class)
    ResponseEntity<ApiProblem> publicProductNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.create(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The resource was not found"));
    }

    @ExceptionHandler(OwnedAddressNotFoundException.class)
    ResponseEntity<ApiProblem> ownedAddressNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.create(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The resource was not found"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiProblem> invalidInput(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.create(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
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
