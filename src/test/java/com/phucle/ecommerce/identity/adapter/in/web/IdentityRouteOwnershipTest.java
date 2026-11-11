package com.phucle.ecommerce.identity.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class IdentityRouteOwnershipTest {

    @Test
    void identityControllerOwnsLoginRoute() {
        assertThat(Arrays.stream(IdentityController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value())))
                .contains("/auth/login");
    }
}
