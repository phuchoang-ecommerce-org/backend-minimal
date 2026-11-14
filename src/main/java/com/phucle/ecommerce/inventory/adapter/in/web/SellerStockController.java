package com.phucle.ecommerce.inventory.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.phucle.ecommerce.identity.api.CurrentActor;
import com.phucle.ecommerce.inventory.application.SellerStockCommands;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class SellerStockController {
    private final CurrentActor actor;
    private final SellerStockCommands commands;
    private final SellerStockWebMapper mapper;
    SellerStockController(CurrentActor actor, SellerStockCommands commands, SellerStockWebMapper mapper) {
        this.actor = actor; this.commands = commands; this.mapper = mapper;
    }
    @PatchMapping("/seller/stock/{variantId}")
    StockResponse replace(@PathVariable UUID variantId, @RequestBody JsonNode body) {
        StockUpdateRequest request = StockUpdateRequest.parse(body);
        return mapper.toResponse(commands.replace(actor.require(), variantId, mapper.toPayload(request)));
    }
}
