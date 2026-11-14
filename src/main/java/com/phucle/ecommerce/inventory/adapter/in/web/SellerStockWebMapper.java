package com.phucle.ecommerce.inventory.adapter.in.web;

import com.phucle.ecommerce.inventory.application.SellerStockCommands;
import org.mapstruct.Builder;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR, typeConversionPolicy = ReportingPolicy.ERROR,
        builder = @Builder(disableBuilder = true))
interface SellerStockWebMapper {
    SellerStockCommands.Update toPayload(StockUpdateRequest request);
    StockResponse toResponse(SellerStockCommands.Stock stock);
}
