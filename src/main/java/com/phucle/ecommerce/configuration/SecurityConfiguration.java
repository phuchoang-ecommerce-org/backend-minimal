package com.phucle.ecommerce.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.phucle.ecommerce.identity.adapter.out.security.TrustedJwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain foundationSecurityFilterChain(
            HttpSecurity http, ProblemResponseWriter problemWriter, TrustedJwtAuthenticationConverter jwtConverter) throws Exception {
        return http
                // Guest-cart mutations keep CSRF/origin controls. Credential JSON endpoints do not use a cookie proof.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/dev/quick-login", "/accounts", "/auth/login", "/cart", "/cart/lines/**", "/me/addresses", "/orders"))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setHeader("WWW-Authenticate", "Bearer");
                            problemWriter.write(response, org.springframework.http.HttpStatus.UNAUTHORIZED,
                                    "AUTHENTICATION_REQUIRED", "Authentication is required");
                        }))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setHeader("WWW-Authenticate", "Bearer");
                            problemWriter.write(response, org.springframework.http.HttpStatus.UNAUTHORIZED,
                                    "AUTHENTICATION_REQUIRED", "Authentication is required");
                        })
                        .accessDeniedHandler((request, response, exception) -> problemWriter.write(
                                response, org.springframework.http.HttpStatus.FORBIDDEN,
                                "ROLE_DENIED", "Access is denied")))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/auth/login", "/accounts", "/dev/quick-login").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/categories", "/products", "/products/{productId}").permitAll()
                        .requestMatchers("/cart", "/cart/lines/**").permitAll()
                        .requestMatchers("/me/addresses").hasRole("MEMBER")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/orders").hasRole("MEMBER")
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
