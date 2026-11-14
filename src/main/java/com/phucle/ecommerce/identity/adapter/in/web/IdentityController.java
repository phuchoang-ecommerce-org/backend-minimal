package com.phucle.ecommerce.identity.adapter.in.web;

import com.phucle.ecommerce.identity.api.CompleteLogin;
import com.phucle.ecommerce.identity.api.CompletedLogin;
import com.phucle.ecommerce.identity.api.RegisterMember;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
class IdentityController {

    private final RegisterMember registerMember;
    private final CompleteLogin completeLogin;
    private final IdentityWebMapper mapper;

    IdentityController(RegisterMember registerMember, CompleteLogin completeLogin, IdentityWebMapper mapper) {
        this.registerMember = registerMember;
        this.completeLogin = completeLogin;
        this.mapper = mapper;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse register(@Valid @RequestBody RegisterMemberRequest request) {
        return mapper.toResponse(registerMember.register(mapper.toCommand(request)));
    }

    @PostMapping("/auth/login")
    LoginResponse logIn(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                        HttpServletResponse httpResponse) {
        var result = completeLogin.complete(mapper.toCommand(request), guestProof(httpRequest),
                httpRequest.getHeader("Origin"));
        clearConsumedGuestCookie(httpResponse, result);
        return response(result);
    }

    private void clearConsumedGuestCookie(HttpServletResponse httpResponse, CompletedLogin result) {
        if (result.merged() || result.sourceConsumed()) {
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("guest_cart", "")
                    .path("/").httpOnly(true).secure(result.secureCookie()).sameSite("Lax")
                    .maxAge(0).build().toString());
        }
    }

    private LoginResponse response(CompletedLogin result) {
        return new LoginResponse(result.login().accessToken(), "Bearer", result.login().expiresInSeconds(),
                mapper.toResponse(result.login().account()),
                new CartMergeResponse(result.merged(), result.rejectedLines()));
    }

    private String guestProof(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals("guest_cart")) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
