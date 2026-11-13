package com.phucle.ecommerce.ordering.adapter.in.web;

import com.phucle.ecommerce.identity.api.CurrentActor;
import com.phucle.ecommerce.ordering.application.OwnOrderQueries;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class OrderQueryController {
    private final OwnOrderQueries queries;
    private final CurrentActor actor;
    private final CheckoutWebMapper mapper;
    OrderQueryController(OwnOrderQueries queries, CurrentActor actor, CheckoutWebMapper mapper) {
        this.queries = queries; this.actor = actor; this.mapper = mapper;
    }
    @GetMapping("/orders/{orderId}")
    CheckoutResponse detail(@PathVariable UUID orderId) {
        return mapper.toResponse(queries.detail(actor.require(), orderId));
    }
    @GetMapping("/orders")
    PageResponse page(@RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        OwnOrderQueries.Page result = queries.page(actor.require(), cursor, limit);
        List<CheckoutResponse> items = result.items().stream().map(mapper::toResponse).toList();
        return new PageResponse(items, result.nextCursor(), result.hasNext());
    }
    record PageResponse(List<CheckoutResponse> items, String nextCursor, boolean hasNext) {}
}
