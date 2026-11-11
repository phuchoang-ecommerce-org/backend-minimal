package com.phucle.ecommerce.identity.application;

import com.phucle.ecommerce.identity.domain.Address;

public interface AddressRepositoryPort {
    void insert(Address address);
}
