package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.identity.api.TrustedActor;
import com.phucle.ecommerce.shared.CursorCodec;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnOrderQueryService implements OwnOrderQueries {
    private final OwnOrderReadPort reads;
    private final CursorCodec cursors;
    public OwnOrderQueryService(OwnOrderReadPort reads, CursorCodec cursors) {
        this.reads = reads; this.cursors = cursors;
    }
    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderView detail(TrustedActor actor, UUID orderId) {
        UUID memberId = member(actor);
        if (orderId == null) throw new IllegalArgumentException("Invalid order ID");
        return reads.findOwnedAll(memberId, List.of(orderId)).stream().findFirst().map(OwnOrderViewMapper::toView)
                .orElseThrow(() -> new OrderFailure("RESOURCE_NOT_FOUND"));
    }
    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page page(TrustedActor actor, String cursor, int limit) {
        UUID memberId = member(actor);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid limit");
        CursorCodec.TimedBoundary boundary = cursor == null ? null
                : cursors.decodeTimed(cursor, "member-orders", memberId.toString());
        List<OwnOrderReadPort.Boundary> rows = reads.pageOwned(memberId,
                boundary == null ? null : boundary.createdAt(), boundary == null ? null : boundary.id(), limit + 1);
        boolean hasNext = rows.size() > limit;
        List<OwnOrderReadPort.Boundary> selected = hasNext ? rows.subList(0, limit) : rows;
        List<OrderView> items = reads.findOwnedAll(memberId, selected.stream().map(OwnOrderReadPort.Boundary::id).toList())
                .stream().map(OwnOrderViewMapper::toView).toList();
        String next = hasNext ? cursors.encodeTimed("member-orders", memberId.toString(),
                selected.getLast().createdAt(), selected.getLast().id()) : null;
        return new Page(items, next, hasNext);
    }
    private UUID member(TrustedActor actor) {
        if (actor == null || !"MEMBER".equals(actor.operationalRole())) throw new OrderFailure("ROLE_DENIED");
        return actor.accountId();
    }
}
