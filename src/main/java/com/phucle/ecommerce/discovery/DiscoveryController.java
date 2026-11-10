package com.phucle.ecommerce.discovery;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class DiscoveryController {
    private final DiscoveryQueryPort queries;
    DiscoveryController(DiscoveryQueryPort queries) { this.queries = queries; }

    @GetMapping("/categories")
    DiscoveryQueryPort.Page<?> categories(@RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return queries.categories(cursor, limit);
    }

    @GetMapping("/products")
    DiscoveryQueryPort.Page<?> products(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String minPrice,
            @RequestParam(required = false) String maxPrice,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return queries.products(q, categoryId, amount(minPrice), amount(maxPrice), cursor, limit);
    }

    @GetMapping("/products/{productId}")
    DiscoveryQueryPort.Product product(@PathVariable UUID productId) { return queries.product(productId); }

    private BigDecimal amount(String value) {
        if (value == null) return null;
        if (!value.matches("(0|[1-9][0-9]{0,9})\\.[0-9]{2}")) throw new IllegalArgumentException("Invalid price");
        return new BigDecimal(value);
    }
}
