package com.phucle.ecommerce.ordering.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record CheckoutRequest(@NotNull UUID addressId, @NotNull @Min(0) Long cartVersion,
                       @NotNull String paymentMethod, @Size(min = 1, max = 100) String voucherCode) {}
