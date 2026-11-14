# B2 — Expected Load and SQL

## Repeat manifest

| Variant | Run | Run ID | Implementation SHA | Script SHA | Fixture seed | Migration | JDK | PostgreSQL | k6 | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| load | 1 | — | — | — | — | — | — | — | — | NOT_RUN |
| load | 2 | — | — | — | — | — | — | — | — | NOT_RUN |
| load | 3 | — | — | — | — | — | — | — | — | NOT_RUN |

## Fixture and workload inputs

| Parameter | Unit | Planned | Observed |
| --- | --- | --- | --- |
| Members | count | 1000 | — |
| Sellers | count | 100 | — |
| Products | count | 10000 | — |
| Variants | count | 20000 | — |
| Historical orders | count | 50000 | — |
| Order lines | count | 100000 | — |
| Maximum single-cardinality scale | × | 10 | — |
| Discovery iteration mix | % | 70 | — |
| Cart/read iteration mix | % | 20 | — |
| Checkout/lifecycle iteration mix | % | 10 | — |
| Frozen R | iterations/s | — | — |
| JWT renewal interval | s | <900 | — |
| Warm-up excluded | s | >=120 | — |

## Load plateaus

| Run | Stage | R multiplier | Duration s | Offered iter/s | Started iter/s | Completed iter/s | Requests/s | Dropped | Preallocated VUs | Max VUs | Read p95 ms | Write p95 ms | All p99 ms | Unexpected % | Expected 409 | Orders/s | Invariant failures | Generator CPU % |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | R | 1 | 900 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | 2R | 2 | 900 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | recovery | 0 | 300 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | R | 1 | 900 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | 2R | 2 | 900 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | recovery | 0 | 300 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | R | 1 | 900 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | 2R | 2 | 900 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | recovery | 0 | 300 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |

## Per-operation measurements

| Scenario | Operation | Stage | Run | Samples | Offered iter/s | Completed iter/s | p50 ms | p95 ms | p99 ms | Max ms | Success | Expected 409 | Unexpected | Timeout |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BM-01 | listCategories | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | listCategories | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | listCategories | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | listCategories | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | listCategories | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | listCategories | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | listProducts | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | listProducts | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | listProducts | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | listProducts | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | listProducts | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | listProducts | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | getProduct | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | getProduct | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | getProduct | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | getProduct | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | getProduct | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-01 | getProduct | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | getOwnCart | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | getOwnCart | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | getOwnCart | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | getOwnCart | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | getOwnCart | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | getOwnCart | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | setCartLine | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | setCartLine | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | setCartLine | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | setCartLine | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | setCartLine | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | setCartLine | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | removeCartLine | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | removeCartLine | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | removeCartLine | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | removeCartLine | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | removeCartLine | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-03 | removeCartLine | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnCheckoutAddresses | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnCheckoutAddresses | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnCheckoutAddresses | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnCheckoutAddresses | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnCheckoutAddresses | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnCheckoutAddresses | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | placeCodOrder | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | placeCodOrder | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | placeCodOrder | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | placeCodOrder | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | placeCodOrder | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | placeCodOrder | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | getOwnOrder | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | getOwnOrder | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | getOwnOrder | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | getOwnOrder | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | getOwnOrder | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | getOwnOrder | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnOrders | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnOrders | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnOrders | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnOrders | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnOrders | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-04 | listOwnOrders | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | cancelUnpaidOrder | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | cancelUnpaidOrder | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | cancelUnpaidOrder | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | cancelUnpaidOrder | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | cancelUnpaidOrder | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | cancelUnpaidOrder | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | listOwnSellerOrders | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | listOwnSellerOrders | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | listOwnSellerOrders | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | listOwnSellerOrders | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | listOwnSellerOrders | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | listOwnSellerOrders | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | markOwnOrderPaid | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | markOwnOrderPaid | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | markOwnOrderPaid | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | markOwnOrderPaid | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | markOwnOrderPaid | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-07 | markOwnOrderPaid | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | getOwnProduct | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | getOwnProduct | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | getOwnProduct | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | getOwnProduct | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | getOwnProduct | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | getOwnProduct | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | updateOwnStock | R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | updateOwnStock | R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | updateOwnStock | R | 3 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | updateOwnStock | 2R | 1 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | updateOwnStock | 2R | 2 | — | — | — | — | — | — | — | — | — | — | — |
| BM-08 | updateOwnStock | 2R | 3 | — | — | — | — | — | — | — | — | — | — | — |

## Resource and persistence

| Run | Stage | App CPU % | App memory % | DB CPU % | DB memory % | Pool active max | Pool pending max | Pool acquire p95 ms | Lock wait p95 ms | Commit p95 ms | Stock delta units | Audit mismatch |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | R | — | — | — | — | — | — | — | — | — | — | — |
| 1 | 2R | — | — | — | — | — | — | — | — | — | — | — |
| 1 | recovery | — | — | — | — | — | — | — | — | — | — | — |
| 2 | R | — | — | — | — | — | — | — | — | — | — | — |
| 2 | 2R | — | — | — | — | — | — | — | — | — | — | — |
| 2 | recovery | — | — | — | — | — | — | — | — | — | — | — |
| 3 | R | — | — | — | — | — | — | — | — | — | — | — |
| 3 | 2R | — | — | — | — | — | — | — | — | — | — | — |
| 3 | recovery | — | — | — | — | — | — | — | — | — | — | — |

## SQL plans

| Query | Dataset | Run | Rows estimated | Rows actual | Rows returned | Shared hit blocks | Shared read blocks | Temp blocks | Sort spill KiB | Execution ms | Pool acquire ms | Commit ms | Blocked sessions | Plan hash |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| product_search | small | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| product_search | small | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| product_search | small | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| product_search | scaled | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| product_search | scaled | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| product_search | scaled | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| own_order_cursor | small | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| own_order_cursor | small | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| own_order_cursor | small | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| own_order_cursor | scaled | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| own_order_cursor | scaled | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| own_order_cursor | scaled | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| seller_portion_list | small | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| seller_portion_list | small | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| seller_portion_list | small | 3 | — | — | — | — | — | — | — | — | — | — | — | — |
| seller_portion_list | scaled | 1 | — | — | — | — | — | — | — | — | — | — | — | — |
| seller_portion_list | scaled | 2 | — | — | — | — | — | — | — | — | — | — | — | — |
| seller_portion_list | scaled | 3 | — | — | — | — | — | — | — | — | — | — | — | — |

## Contention

| Run | Fixture | Competing carts | Initial stock units | Successful orders | Expected conflicts | Negative stock rows | Partial order rows | Duplicate key rows | Audit mismatch | Lock wait p95 ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | last_unit | — | 1 | — | — | — | — | — | — | — |
| 2 | last_unit | — | 1 | — | — | — | — | — | — | — |
| 3 | last_unit | — | 1 | — | — | — | — | — | — | — |
| 1 | adequate_stock | — | — | — | — | — | — | — | — | — |
| 2 | adequate_stock | — | — | — | — | — | — | — | — | — |
| 3 | adequate_stock | — | — | — | — | — | — | — | — | — |
