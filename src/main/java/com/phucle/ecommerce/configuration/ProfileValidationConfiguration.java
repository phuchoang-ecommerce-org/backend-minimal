package com.phucle.ecommerce.configuration;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
class ProfileValidationConfiguration {

    @Bean
    ApplicationRunner profilePolicyValidator(Environment environment) {
        return arguments -> ProfilePolicy.requireValid(
                environment.getActiveProfiles(),
                environment.getDefaultProfiles(),
                environment.getProperty("marketplace.dev-fixtures.enabled", Boolean.class, false));
    }
}
