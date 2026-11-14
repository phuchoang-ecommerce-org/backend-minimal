package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.audit.api.OrderAudit;
import com.phucle.ecommerce.identity.api.TrustedActor;
import com.phucle.ecommerce.inventory.api.CheckoutStock;
import com.phucle.ecommerce.ordering.domain.CustomerOrder;
import com.phucle.ecommerce.ordering.domain.OrderStateCorruption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelOrderService implements CancelOrder {
    private final OrderWritePort orders;
    private final CheckoutStock stock;
    private final OrderAudit audit;
    private final CancelOrderResultReadPort results;

    public CancelOrderService(OrderWritePort orders, CheckoutStock stock, OrderAudit audit, CancelOrderResultReadPort results) {
        this.orders = orders; this.stock = stock; this.audit = audit; this.results = results;
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public OwnOrderQueries.OrderView execute(TrustedActor actor, UUID orderId) {
        if (actor == null || !"MEMBER".equals(actor.operationalRole())) throw new OrderFailure("ROLE_DENIED");
        if (orderId == null) throw new IllegalArgumentException("Invalid order ID");
        var order = orders.lockOwned(actor.accountId(), orderId)
                .orElseThrow(() -> new OrderFailure("RESOURCE_NOT_FOUND"));
        cancelIfEligible(actor.accountId(), order);
        // The root remains locked until commit, so payment cannot change a portion between projection reads.
        return results.findOwned(actor.accountId(), orderId).map(OwnOrderViewMapper::toView)
                .orElseThrow(() -> new OrderStateCorruption("Cancelled order vanished before response projection"));
    }

    private void cancelIfEligible(UUID memberId, CustomerOrder order) {
        try {
            if (order.cancel(Instant.now())) {
                stock.restore(quantities(order));
                orders.saveCancellation(order);
                audit.cancelled(memberId, order.id());
            }
        } catch (CustomerOrder.CancellationBlocked exception) {
            throw new OrderFailure("ILLEGAL_ORDER_STATE");
        }
    }

    private Map<UUID, Integer> quantities(CustomerOrder order) {
        Map<UUID, Integer> quantities = new HashMap<>();
        order.portions().forEach(portion -> portion.lines().forEach(line ->
                quantities.merge(line.snapshot().variantId(), line.snapshot().quantity(), Math::addExact)));
        return quantities;
    }
}
