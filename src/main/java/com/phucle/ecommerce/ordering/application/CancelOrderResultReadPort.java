package com.phucle.ecommerce.ordering.application;

import java.util.Optional;
import java.util.UUID;

/** Owner-filtered projection used by cancellation while its root lock is held. */
public interface CancelOrderResultReadPort {
    Optional<OwnOrderReadPort.Order> findOwned(UUID memberId, UUID orderId);
}
