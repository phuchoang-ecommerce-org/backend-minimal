package com.phucle.ecommerce.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
class ProblemResponseWriter {

    private final ObjectMapper objectMapper;
    private final ApiProblemFactory problems;

    ProblemResponseWriter(ObjectMapper objectMapper, ApiProblemFactory problems) {
        this.objectMapper = objectMapper;
        this.problems = problems;
    }

    void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problems.create(status, code, message));
    }
}
