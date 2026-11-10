package com.phucle.ecommerce.configuration;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class ApiProblemFactory {

    ApiProblem create(HttpStatus status, String code, String message) {
        return create(status, code, message, Map.of());
    }

    ApiProblem create(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        return new ApiProblem(
                "about:blank",
                status.getReasonPhrase(),
                status.value(),
                code,
                message,
                UUID.randomUUID().toString(),
                fieldErrors);
    }
}
