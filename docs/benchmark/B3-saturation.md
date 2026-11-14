# B3 — Saturation and Recovery

## Repeat manifest

| Variant | Run | Run ID | Implementation SHA | Script SHA | Fixture seed | Migration | JDK | PostgreSQL | k6 | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| stress | 1 | — | — | — | — | — | — | — | — | NOT_RUN |
| stress | 2 | — | — | — | — | — | — | — | — | NOT_RUN |
| stress | 3 | — | — | — | — | — | — | — | — | NOT_RUN |
| spike | 1 | — | — | — | — | — | — | — | — | NOT_RUN |
| spike | 2 | — | — | — | — | — | — | — | — | NOT_RUN |
| spike | 3 | — | — | — | — | — | — | — | — | NOT_RUN |

## Observation settings

| Parameter | Unit | Planned | Observed |
| --- | --- | --- | --- |
| Prometheus scrape interval | s | 15 | — |
| Prometheus local retention | h | 24 | — |
| Frozen R | iterations/s | — | — |
| Maximum initial stress rate | R multiplier | 3 | — |
| Recovery observation | s | 300 | — |

## Offered-load schedule

| Workload | Stage | R multiplier | Duration s | Entry condition |
| --- | --- | --- | --- | --- |
| stress | stress_0.5R | 0.5 | 300 | initial |
| stress | stress_1R | 1 | 300 | initial |
| stress | stress_1.5R | 1.5 | 300 | initial |
| stress | stress_2R | 2 | 300 | initial |
| stress | stress_2.5R | 2.5 | 300 | stop_limits_clear |
| stress | stress_3R | 3 | 300 | stop_limits_clear |
| spike | spike_1_R_before | 1 | 300 | initial |
| spike | spike_1_3R | 3 | 30 | initial |
| spike | spike_1_R_after | 1 | 300 | initial |
| spike | spike_2_R_before | 1 | 300 | recovered |
| spike | spike_2_3R | 3 | 30 | recovered |
| spike | spike_2_R_after | 1 | 300 | recovered |

## Stop and recovery limits

| Metric | Unit | Operator | Threshold | Window s | Observed |
| --- | --- | --- | --- | --- | --- |
| Invariant failures | count | > | 0 | 0 | — |
| DB disk free | % | < | 20 | 0 | — |
| App memory used | % | > | 90 | 60 | — |
| Unexpected errors | % | > | 5 | 60 | — |
| p99 latency | ms | > | 5000 | 60 | — |
| Return to baseline | s | <= | 300 | 300 | — |

## Stage measurements

| Run | Workload | Stage | Offered iter/s | Completed iter/s | Requests/s | Dropped | Read p95 ms | Write p95 ms | p99 ms | Unexpected % | App CPU % | App memory % | DB disk free % | Pool pending max | Pool wait p95 ms | Invariant failures | Recovery s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | stress | stress_0.5R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | stress | stress_1R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | stress | stress_1.5R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | stress | stress_2R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | stress | stress_2.5R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | stress | stress_3R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_1_R_before | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_1_3R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_1_R_after | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_2_R_before | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_2_3R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_2_R_after | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_0.5R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_1R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_1.5R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_2R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_2.5R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_3R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_1_R_before | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_1_3R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_1_R_after | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_2_R_before | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_2_3R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_2_R_after | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_0.5R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_1R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_1.5R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_2R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_2.5R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_3R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_1_R_before | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_1_3R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_1_R_after | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_2_R_before | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_2_3R | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_2_R_after | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |

## Per-operation tails

| Run | Workload | Stage | Scenario ID | Operation | Samples | Offered iter/s | Completed iter/s | p95 ms | p99 ms | Unexpected | Expected 409 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | stress | stress_0.5R | — | — | — | — | — | — | — | — | — |
| 1 | stress | stress_1R | — | — | — | — | — | — | — | — | — |
| 1 | stress | stress_1.5R | — | — | — | — | — | — | — | — | — |
| 1 | stress | stress_2R | — | — | — | — | — | — | — | — | — |
| 1 | stress | stress_2.5R | — | — | — | — | — | — | — | — | — |
| 1 | stress | stress_3R | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_1_R_before | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_1_3R | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_1_R_after | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_2_R_before | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_2_3R | — | — | — | — | — | — | — | — | — |
| 1 | spike | spike_2_R_after | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_0.5R | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_1R | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_1.5R | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_2R | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_2.5R | — | — | — | — | — | — | — | — | — |
| 2 | stress | stress_3R | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_1_R_before | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_1_3R | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_1_R_after | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_2_R_before | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_2_3R | — | — | — | — | — | — | — | — | — |
| 2 | spike | spike_2_R_after | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_0.5R | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_1R | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_1.5R | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_2R | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_2.5R | — | — | — | — | — | — | — | — | — |
| 3 | stress | stress_3R | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_1_R_before | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_1_3R | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_1_R_after | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_2_R_before | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_2_3R | — | — | — | — | — | — | — | — | — |
| 3 | spike | spike_2_R_after | — | — | — | — | — | — | — | — | — |

## Capacity boundary

| Run | Workload | Last healthy iter/s | First breached iter/s | Knee iter/s | First breached p99 ms | Stop offset s | In-flight requests | Committed after stop | Recovered within 300 s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | stress | — | — | — | — | — | — | — | — |
| 2 | stress | — | — | — | — | — | — | — | — |
| 3 | stress | — | — | — | — | — | — | — | — |
| 1 | spike | — | — | — | — | — | — | — | — |
| 2 | spike | — | — | — | — | — | — | — | — |
| 3 | spike | — | — | — | — | — | — | — | — |
