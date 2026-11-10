package com.phucle.ecommerce.identity.adapter.in.web;

import com.phucle.ecommerce.identity.api.LogIn;
import com.phucle.ecommerce.identity.api.RegisterMember;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
class IdentityController {

    private final RegisterMember registerMember;
    private final LogIn logIn;
    private final IdentityWebMapper mapper;

    IdentityController(RegisterMember registerMember, LogIn logIn, IdentityWebMapper mapper) {
        this.registerMember = registerMember;
        this.logIn = logIn;
        this.mapper = mapper;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse register(@Valid @RequestBody RegisterMemberRequest request) {
        return mapper.toResponse(registerMember.register(mapper.toCommand(request)));
    }

    @PostMapping("/auth/login")
    LoginResponse logIn(@Valid @RequestBody LoginRequest request) {
        var result = logIn.logIn(mapper.toCommand(request));
        return new LoginResponse(
                result.accessToken(),
                "Bearer",
                result.expiresInSeconds(),
                mapper.toResponse(result.account()),
                new CartMergeResponse(false, List.of()));
    }
}
