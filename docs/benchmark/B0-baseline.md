# B0 — Reproducible Baseline

## Run controls

| Parameter | Unit | Planned | Run 1 | Run 2 | Run 3 |
| --- | --- | --- | --- | --- | --- |
| Independent repeats | count | 3 | — | — | — |
| Calibration rates | iterations/s | 1, 5, 10 | — | — | — |
| Calibration duration per rate | s | 120 | — | — | — |
| Minimum warm-up | s | 120 | — | — | — |
| Measurement per repeat | s | 600 | — | — | — |
| JWT lifetime | s | 900 | — | — | — |
| Starter sellers | count | 2 | — | — | — |
| Starter members | count | 2 | — | — | — |
| Starter seller portions per checkout | count | 2 | — | — | — |
| Frozen R | iterations/s | — | — | — | — |
| Fixture seed | integer | — | — | — | — |
| Shipped operation count | count | 12 or 18 | — | — | — |
| Generator maximum VUs | count | — | — | — | — |
| App CPU quota | cores | — | — | — | — |
| App memory limit | MiB | — | — | — | — |
| DB CPU quota | cores | — | — | — | — |
| DB memory limit | MiB | — | — | — | — |
| Cache state | class | — | — | — | — |

## Diagnostic budgets

| Metric | Unit | Operator | Threshold | Class | Run 1 | Run 2 | Run 3 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Read p95 | ms | <= | 300 | HYPOTHESIS | — | — | — |
| Write p95 | ms | <= | 800 | HYPOTHESIS | — | — | — |
| All-route p99 | ms | <= | 2000 | HYPOTHESIS | — | — | — |
| Unexpected failure rate | % | < | 1 | HYPOTHESIS | — | — | — |
| Invariant failures | count | = | 0 | REQUIRED | — | — | — |
| Dropped iterations at R | count | = | 0 | REQUIRED | — | — | — |

## Repeat manifest

| Run | Run ID | Implementation SHA | Script SHA | Contract SHA | Migration | JDK | Spring Boot | PostgreSQL | k6 | Fixture rows | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | — | — | — | — | — | — | — | — | — | — | NOT_RUN |
| 2 | — | — | — | — | — | — | — | — | — | — | NOT_RUN |
| 3 | — | — | — | — | — | — | — | — | — | — | NOT_RUN |

## Operation measurements

| Wave | Scenario | Operation | Variant | Run | Samples | Offered iter/s | Completed iter/s | p50 ms | p95 ms | p99 ms | Max ms | Success | Expected 409 | Unexpected | Timeout | Response KiB |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| initial | BM-01 | listCategories | default | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listCategories | default | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listCategories | default | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | unfiltered | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | unfiltered | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | unfiltered | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | category_filter | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | category_filter | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | category_filter | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | common_search | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | common_search | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | common_search | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | rare_search | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | rare_search | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | rare_search | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | no_result_search | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | no_result_search | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | no_result_search | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | cursor_limit_20 | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | cursor_limit_20 | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | cursor_limit_20 | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | cursor_limit_100 | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | cursor_limit_100 | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | listProducts | cursor_limit_100 | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | getProduct | default | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | getProduct | default | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-01 | getProduct | default | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-04 | placeCodOrder | two_seller | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-04 | placeCodOrder | two_seller | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-04 | placeCodOrder | two_seller | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-04 | getOwnOrder | after_checkout | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-04 | getOwnOrder | after_checkout | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-04 | getOwnOrder | after_checkout | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-04 | checkout_journey | two_seller | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-04 | checkout_journey | two_seller | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-04 | checkout_journey | two_seller | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-05 | placeCodOrder | same_key_same_body | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-05 | placeCodOrder | same_key_same_body | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-05 | placeCodOrder | same_key_same_body | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-05 | placeCodOrder | same_key_changed_body_409 | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-05 | placeCodOrder | same_key_changed_body_409 | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| initial | BM-05 | placeCodOrder | same_key_changed_body_409 | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-02 | logIn | separate_auth | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-02 | logIn | separate_auth | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-02 | logIn | separate_auth | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-02 | registerMember | unique_identity | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-02 | registerMember | unique_identity | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-02 | registerMember | unique_identity | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | getOwnCart | member | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | getOwnCart | member | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | getOwnCart | member | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | setCartLine | normal_edit | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | setCartLine | normal_edit | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | setCartLine | normal_edit | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | removeCartLine | normal_edit | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | removeCartLine | normal_edit | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | removeCartLine | normal_edit | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | logIn | guest_cart_merge | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | logIn | guest_cart_merge | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | logIn | guest_cart_merge | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | setCartLine | stale_version_409 | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | setCartLine | stale_version_409 | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-03 | setCartLine | stale_version_409 | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-04 | listOwnCheckoutAddresses | owned | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-04 | listOwnCheckoutAddresses | owned | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-04 | listOwnCheckoutAddresses | owned | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-04 | createOwnCheckoutAddress | unique_address | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-04 | createOwnCheckoutAddress | unique_address | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-04 | createOwnCheckoutAddress | unique_address | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-04 | listOwnOrders | cursor | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-04 | listOwnOrders | cursor | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-04 | listOwnOrders | cursor | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-07 | cancelUnpaidOrder | normal | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-07 | cancelUnpaidOrder | normal | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-07 | cancelUnpaidOrder | normal | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-07 | listOwnSellerOrders | owned | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-07 | listOwnSellerOrders | owned | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-07 | listOwnSellerOrders | owned | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-07 | markOwnOrderPaid | normal | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-07 | markOwnOrderPaid | normal | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-07 | markOwnOrderPaid | normal | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-08 | getOwnProduct | owned | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-08 | getOwnProduct | owned | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-08 | getOwnProduct | owned | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-08 | updateOwnStock | normal | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-08 | updateOwnStock | normal | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| later | BM-08 | updateOwnStock | normal | 3 | — | — | — | — | — | — | — | — | — | — | — | — |

## Throughput and invariants

| Run | Offered iter/s | Completed iter/s | Requests/s | Dropped | Generator CPU % | Generator memory % | App CPU % | App memory % | DB CPU % | DB memory % | Created orders | Duplicate orders | Stock delta units | Cart mismatch | Seller portion mismatch | Amount mismatch | Audit mismatch | Replay snapshot mismatch | Assertion failures |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
