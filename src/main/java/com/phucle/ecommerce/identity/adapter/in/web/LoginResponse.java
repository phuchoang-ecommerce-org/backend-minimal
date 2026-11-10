package com.phucle.ecommerce.identity.adapter.in.web;

record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        AccountResponse account,
        CartMergeResponse cartMerge) {
}
