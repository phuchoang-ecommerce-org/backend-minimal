package com.phucle.ecommerce.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain foundationSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Keep CSRF enabled. The profile-only absence probe must reach MVC so an
                // unimplemented quick-login path remains 404 rather than a filter 403.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/dev/quick-login"))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // No feature endpoint exists in the foundation. Permitting dispatcher routing keeps an
                // absent prod quick-login route an actual 404; later feature slices add route RBAC.
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}
