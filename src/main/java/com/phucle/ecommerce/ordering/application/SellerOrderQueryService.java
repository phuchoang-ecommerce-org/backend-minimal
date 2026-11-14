package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.identity.api.TrustedActor;
import com.phucle.ecommerce.shared.CursorCodec;
import com.phucle.ecommerce.shared.InvalidCursorException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class SellerOrderQueryService implements SellerOrderQueries {
    private final SellerShopActor seller;
    private final SellerOrderReadPort reads;
    private final CursorCodec cursors;
    SellerOrderQueryService(SellerShopActor seller, SellerOrderReadPort reads, CursorCodec cursors) {
        this.seller = seller; this.reads = reads; this.cursors = cursors;
    }
    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page page(TrustedActor actor, String cursor, int limit) {
        seller.requireSeller(actor);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid limit");
        Optional<UUID> shopId = seller.shopIdFor(actor);
        if (shopId.isEmpty()) {
            if (cursor != null) throw new InvalidCursorException();
            return new Page(List.of(), null, false);
        }
        UUID sellerId = shopId.orElseThrow();
        CursorCodec.TimedBoundary boundary = cursor == null ? null
                : cursors.decodeTimed(cursor, "seller-orders", sellerId.toString());
        List<SellerOrderReadPort.Boundary> rows = reads.pageOwned(sellerId,
                boundary == null ? null : boundary.createdAt(), boundary == null ? null : boundary.id(), limit + 1);
        boolean hasNext = rows.size() > limit;
        List<SellerOrderReadPort.Boundary> selected = hasNext ? rows.subList(0, limit) : rows;
        List<OrderView> views = reads.findOwnedAll(sellerId, selected.stream().map(SellerOrderReadPort.Boundary::id).toList())
                .stream().map(SellerOrderViewMapper::toView).toList();
        String next = hasNext ? cursors.encodeTimed("seller-orders", sellerId.toString(),
                selected.getLast().createdAt(), selected.getLast().id()) : null;
        return new Page(views, next, hasNext);
    }
}
