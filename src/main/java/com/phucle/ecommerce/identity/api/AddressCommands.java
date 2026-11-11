package com.phucle.ecommerce.identity.api;

public interface AddressCommands {
    AddressView create(TrustedActor actor, CreateAddressPayload payload);
}
