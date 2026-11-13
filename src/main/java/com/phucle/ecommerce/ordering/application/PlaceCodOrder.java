package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.identity.api.TrustedActor;
import java.util.UUID;

public interface PlaceCodOrder {
    record Command(UUID addressId, long cartVersion, String paymentMethod, String voucherCode, String idempotencyKey) {}
    CreationSnapshotReadPort.Order execute(TrustedActor actor, Command command);
}
