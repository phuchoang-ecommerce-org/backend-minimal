package com.phucle.ecommerce.ordering.adapter.in.web;

import com.phucle.ecommerce.identity.api.CurrentActor;
import com.phucle.ecommerce.ordering.application.RecordCodPayment;
import com.phucle.ecommerce.ordering.application.SellerOrderQueries;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class SellerOrderController {
    private final CurrentActor actor;
    private final SellerOrderQueries queries;
    private final RecordCodPayment payment;
    private final CheckoutWebMapper mapper;
    SellerOrderController(CurrentActor actor, SellerOrderQueries queries, RecordCodPayment payment, CheckoutWebMapper mapper) {
        this.actor = actor; this.queries = queries; this.payment = payment; this.mapper = mapper;
    }
    @GetMapping("/seller/orders")
    PageResponse page(@RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        SellerOrderQueries.Page result = queries.page(actor.require(), cursor, limit);
        List<CheckoutResponse.SellerOrderResponse> items = result.items().stream().map(mapper::toResponse).toList();
        return new PageResponse(items, result.nextCursor(), result.hasNext());
    }
    @PostMapping("/seller/orders/{sellerOrderId}/mark-paid")
    CheckoutResponse.SellerOrderResponse markPaid(@PathVariable UUID sellerOrderId) {
        return mapper.toResponse(payment.execute(actor.require(), sellerOrderId));
    }
    record PageResponse(List<CheckoutResponse.SellerOrderResponse> items, String nextCursor, boolean hasNext) {}
}
