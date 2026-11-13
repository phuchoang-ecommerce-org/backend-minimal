package com.phucle.ecommerce.ordering.adapter.in.web;

import com.phucle.ecommerce.identity.api.CurrentActor;
import com.phucle.ecommerce.ordering.application.CancelOrder;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class OrderCancellationController {
    private final CancelOrder cancel;
    private final CurrentActor actor;
    private final CheckoutWebMapper mapper;
    OrderCancellationController(CancelOrder cancel, CurrentActor actor, CheckoutWebMapper mapper) {
        this.cancel = cancel; this.actor = actor; this.mapper = mapper;
    }
    @PostMapping("/orders/{orderId}/cancellation")
    CheckoutResponse cancel(@PathVariable UUID orderId) {
        return mapper.toResponse(cancel.execute(actor.require(), orderId));
    }
}
