package com.phucle.ecommerce.cart.adapter.in.web;

import com.phucle.ecommerce.cart.api.GuestOriginPolicy;
import com.phucle.ecommerce.cart.application.port.in.CartCommands;
import com.phucle.ecommerce.cart.application.port.in.CartQueries;
import com.phucle.ecommerce.cart.application.port.in.CartView;
import com.phucle.ecommerce.cart.application.port.in.GuestCart;
import com.phucle.ecommerce.identity.api.CurrentActor;
import com.phucle.ecommerce.identity.api.TrustedActor;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class CartController {
    record WriteLine(Integer quantity, Long expectedVersion) {}
    private final CartCommands commands;
    private final CartQueries queries;
    private final GuestOriginPolicy guestPolicy;
    private final CurrentActor currentActor;
    CartController(CartCommands commands, CartQueries queries, GuestOriginPolicy guestPolicy, CurrentActor currentActor) {
        this.commands = commands; this.queries=queries; this.guestPolicy = guestPolicy; this.currentActor = currentActor;
    }
    @GetMapping("/cart")
    CartView get(HttpServletRequest request, HttpServletResponse response) {
        TrustedActor actor = actor();
        if (actor != null) return queries.getMemberCart(actor);
        GuestCart guest = commands.getOrCreateGuest(proof(request));
        if (guest.proof() != null) response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(guest.proof(), guestPolicy.guestTtlSeconds(), guestPolicy.secureCookie()));
        return guest.cart();
    }
    @PutMapping("/cart/lines/{variantId}")
    CartView set(@PathVariable UUID variantId, @RequestBody WriteLine body, HttpServletRequest request) {
        TrustedActor actor = actor();
        String proof = proof(request);
        if (actor == null && proof != null) guestPolicy.requireAllowed(request.getHeader("Origin"));
        if (body.quantity() == null || body.expectedVersion() == null) throw new IllegalArgumentException();
        return commands.setLine(actor, proof, variantId, body.quantity(), body.expectedVersion());
    }
    @DeleteMapping("/cart/lines/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID variantId, @RequestParam long expectedVersion, HttpServletRequest request) {
        TrustedActor actor = actor();
        String proof = proof(request);
        if (actor == null && proof != null) guestPolicy.requireAllowed(request.getHeader("Origin"));
        commands.removeLine(actor, proof, variantId, expectedVersion);
    }
    private TrustedActor actor() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken) {
            return currentActor.require();
        }
        return null;
    }
    public static String proof(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (cookie.getName().equals("guest_cart")) return cookie.getValue();
        return null;
    }
    public static String cookie(String proof, long maxAge, boolean secure) {
        return ResponseCookie.from("guest_cart", proof).path("/").httpOnly(true).secure(secure)
                .sameSite("Lax").maxAge(maxAge).build().toString();
    }
}
