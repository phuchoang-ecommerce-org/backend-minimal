package com.phucle.ecommerce.inventory.adapter.in.web;

import java.util.UUID;

record StockResponse(UUID variantId, int onHand, long version) {}
