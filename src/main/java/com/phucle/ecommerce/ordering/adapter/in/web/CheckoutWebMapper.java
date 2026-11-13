package com.phucle.ecommerce.ordering.adapter.in.web;

import com.phucle.ecommerce.ordering.application.CreationSnapshotReadPort;
import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Builder;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR,
        builder = @Builder(disableBuilder = true))
interface CheckoutWebMapper {
    CheckoutResponse toResponse(CreationSnapshotReadPort.Order order);
    CheckoutResponse.AddressResponse toResponse(AddressSnapshot address);
    CheckoutResponse.SellerOrderResponse toResponse(CreationSnapshotReadPort.SellerOrder order);
    CheckoutResponse.PurchasedLineResponse toResponse(CreationSnapshotReadPort.PurchasedLine line);
}
