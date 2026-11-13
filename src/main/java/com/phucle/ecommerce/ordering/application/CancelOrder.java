package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.identity.api.TrustedActor;
import java.util.UUID;

public interface CancelOrder {
    OwnOrderQueries.OrderView execute(TrustedActor actor, UUID orderId);
}
