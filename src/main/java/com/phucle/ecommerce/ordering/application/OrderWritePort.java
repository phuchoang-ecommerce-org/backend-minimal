package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.ordering.domain.CustomerOrder;
import java.util.Optional;
import java.util.UUID;

public interface OrderWritePort {
    record Existing(UUID id, String fingerprint) {}
    void serializeIntent(UUID memberId, String key);
    Optional<Existing> findExisting(UUID memberId, String key);
    void save(String key, String fingerprint, CustomerOrder order);
}
