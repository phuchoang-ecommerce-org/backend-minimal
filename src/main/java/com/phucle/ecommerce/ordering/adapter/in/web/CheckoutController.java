package com.phucle.ecommerce.ordering.adapter.in.web;

import com.phucle.ecommerce.identity.api.CurrentActor;
import com.phucle.ecommerce.ordering.application.PlaceCodOrder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class CheckoutController {
    private final PlaceCodOrder checkout;
    private final CurrentActor actor;
    private final CheckoutWebMapper mapper;
    CheckoutController(PlaceCodOrder checkout, CurrentActor actor, CheckoutWebMapper mapper) {
        this.checkout = checkout; this.actor = actor; this.mapper = mapper;
    }
    @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED)
    CheckoutResponse place(@Valid @RequestBody CheckoutRequest request,
                           @RequestHeader("Idempotency-Key") String key) {
        return mapper.toResponse(checkout.execute(actor.require(), new PlaceCodOrder.Command(request.addressId(),
                request.cartVersion(), request.paymentMethod(), request.voucherCode(), key)));
    }
}
