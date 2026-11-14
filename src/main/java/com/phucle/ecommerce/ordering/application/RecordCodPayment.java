package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.identity.api.TrustedActor;
import java.util.UUID;

public interface RecordCodPayment {
    SellerOrderQueries.OrderView execute(TrustedActor actor, UUID sellerOrderId);
}
