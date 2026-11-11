package com.phucle.ecommerce.identity.adapter.in.web;

import com.phucle.ecommerce.identity.api.CreateAddressPayload;
import com.phucle.ecommerce.identity.api.AddressView;
import org.mapstruct.Builder;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR, builder = @Builder(disableBuilder = true))
interface AddressWebMapper {
    CreateAddressPayload toPayload(AddressRequest request);
    AddressResponse toResponse(AddressView address);
}
