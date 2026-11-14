package com.phucle.ecommerce.discovery;

import org.mapstruct.Builder;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR, typeConversionPolicy = ReportingPolicy.ERROR,
        builder = @Builder(disableBuilder = true))
interface SellerProductWebMapper {
    SellerProductResponse toResponse(DiscoveryQueryPort.Product product);
    SellerProductResponse.Variant toResponse(DiscoveryQueryPort.Variant variant);
    SellerProductResponse.Stock toResponse(DiscoveryQueryPort.Stock stock);
    SellerProductResponse.LinkedImage toResponse(DiscoveryQueryPort.LinkedImage image);
    SellerProductResponse.UploadedImage toResponse(DiscoveryQueryPort.UploadedImage image);

    default SellerProductResponse.Image toResponse(DiscoveryQueryPort.Image image) {
        if (image instanceof DiscoveryQueryPort.LinkedImage linked) return toResponse(linked);
        if (image instanceof DiscoveryQueryPort.UploadedImage uploaded) return toResponse(uploaded);
        throw new IllegalArgumentException("Unknown product image");
    }
}
