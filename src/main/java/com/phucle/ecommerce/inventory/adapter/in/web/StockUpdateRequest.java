package com.phucle.ecommerce.inventory.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;

record StockUpdateRequest(int onHand, long expectedVersion) {
    static StockUpdateRequest parse(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 2 || !body.has("onHand") || !body.has("expectedVersion")
                || !body.get("onHand").isIntegralNumber() || !body.get("onHand").canConvertToInt()
                || !body.get("expectedVersion").isIntegralNumber() || !body.get("expectedVersion").canConvertToLong()) {
            throw new IllegalArgumentException("Invalid stock request");
        }
        int onHand = body.get("onHand").intValue();
        long expectedVersion = body.get("expectedVersion").longValue();
        if (onHand < 0 || expectedVersion < 0) throw new IllegalArgumentException("Invalid stock request");
        return new StockUpdateRequest(onHand, expectedVersion);
    }
}
