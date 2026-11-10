package com.phucle.ecommerce.review.api;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface PublicReviewSummaryPort {
    record Summary(BigDecimal ratingAverage, int reviewCount) {}
    Map<UUID, Summary> summaries(Set<UUID> productIds);
}
