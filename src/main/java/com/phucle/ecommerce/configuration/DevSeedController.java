package com.phucle.ecommerce.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("dev")
@ConditionalOnProperty(prefix = "marketplace.dev-fixtures", name = "enabled", havingValue = "true")
class DevSeedController {
    private final DevFixtureSeedConfiguration fixtures;

    DevSeedController(DevFixtureSeedConfiguration fixtures) {
        this.fixtures = fixtures;
    }

    @PostMapping("/dev/seed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void seed(@Valid @RequestBody SeedRequest request, HttpServletRequest httpRequest) {
        String remote = httpRequest.getRemoteAddr();
        if (!"127.0.0.1".equals(remote) && !"::1".equals(remote) && !"0:0:0:0:0:0:0:1".equals(remote)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Local development only");
        }
        String origin = httpRequest.getHeader("Origin");
        if (origin != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct local requests only");
        }
        try {
            fixtures.seed(request.memberPassword(), request.sellerPassword(), request.adminPassword(), request.data());
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Synthetic fixture identity conflicts with existing data");
        }
    }

    record SeedRequest(@NotBlank @Size(min = 12) String memberPassword,
                       @NotBlank @Size(min = 12) String sellerPassword,
                       @NotBlank @Size(min = 12) String adminPassword,
                       DevSeedDataset data) {
    }
}
