package com.phucle.ecommerce.identity.application;

import com.phucle.ecommerce.identity.api.AddressCommands;
import com.phucle.ecommerce.identity.api.AddressView;
import com.phucle.ecommerce.identity.api.CreateAddressPayload;
import com.phucle.ecommerce.identity.api.TrustedActor;
import com.phucle.ecommerce.identity.domain.Address;
import com.phucle.ecommerce.identity.domain.AddressValue;
import com.phucle.ecommerce.identity.domain.MemberId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AddressCommandService implements AddressCommands {
    private final AddressRepositoryPort repository;
    AddressCommandService(AddressRepositoryPort repository) { this.repository = repository; }
    @Override @Transactional public AddressView create(TrustedActor actor, CreateAddressPayload payload) {
        AddressAuthorization.requireMember(actor);
        if (payload == null) throw new IllegalArgumentException("Address is required");
        AddressValue value = new AddressValue(payload.recipient(), payload.addressLine(), payload.locality(),
                payload.postalCode(), payload.country());
        Address address = Address.create(new MemberId(actor.accountId()), value);
        repository.insert(address);
        return new AddressView(address.id().value(), value.recipient(), value.addressLine(), value.locality(),
                value.postalCode(), value.country());
    }
}
