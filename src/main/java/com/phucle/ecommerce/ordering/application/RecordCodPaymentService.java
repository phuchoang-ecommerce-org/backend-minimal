package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.audit.api.OrderAudit;
import com.phucle.ecommerce.identity.api.TrustedActor;
import com.phucle.ecommerce.ordering.domain.CustomerOrder;
import com.phucle.ecommerce.ordering.domain.OrderStateCorruption;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class RecordCodPaymentService implements RecordCodPayment {
    private final SellerShopActor seller;
    private final OrderWritePort orders;
    private final SellerOrderReadPort reads;
    private final OrderAudit audit;
    RecordCodPaymentService(SellerShopActor seller, OrderWritePort orders, SellerOrderReadPort reads, OrderAudit audit) {
        this.seller = seller; this.orders = orders; this.reads = reads; this.audit = audit;
    }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public SellerOrderQueries.OrderView execute(TrustedActor actor, UUID sellerOrderId) {
        seller.requireSeller(actor);
        UUID sellerId = seller.shopIdFor(actor)
                .orElseThrow(() -> new OrderFailure("RESOURCE_NOT_FOUND"));
        if (sellerOrderId == null) throw new IllegalArgumentException("Invalid portion ID");
        CustomerOrder root = orders.lockOwnedPortion(sellerId, sellerOrderId)
                .orElseThrow(() -> new OrderFailure("RESOURCE_NOT_FOUND"));
        try {
            root.recordCod(sellerOrderId, sellerId, Instant.now());
        } catch (IllegalStateException exception) {
            throw new OrderFailure("ILLEGAL_ORDER_STATE");
        }
        Instant paidAt = root.portions().stream().filter(p -> p.id().equals(sellerOrderId)).findFirst().orElseThrow().paidAt();
        orders.savePayment(root, sellerOrderId);
        audit.sellerPaid(actor.accountId(), sellerOrderId, paidAt);
        return reads.findOwnedAll(sellerId, java.util.List.of(sellerOrderId)).stream().findFirst()
                .map(SellerOrderViewMapper::toView)
                .orElseThrow(() -> new OrderStateCorruption("Paid portion vanished before response projection"));
    }
}
