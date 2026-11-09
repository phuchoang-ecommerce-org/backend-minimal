package com.phucle.ecommerce.configuration;

import java.util.Map;

public record ApiProblem(
        String type,
        String title,
        int status,
        String code,
        String message,
        String correlationId,
        Map<String, String> fieldErrors) {
}
