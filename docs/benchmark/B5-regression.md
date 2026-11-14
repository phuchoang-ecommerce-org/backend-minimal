# B5 — Regression Gate

## Repeat manifest

| Variant | Run | Run ID | Implementation SHA | Script SHA | Fixture seed | Migration | JDK | PostgreSQL | k6 | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| baseline | 1 | — | — | — | — | — | — | — | — | NOT_RUN |
| baseline | 2 | — | — | — | — | — | — | — | — | NOT_RUN |
| baseline | 3 | — | — | — | — | — | — | — | — | NOT_RUN |
| candidate | 1 | — | — | — | — | — | — | — | — | NOT_RUN |
| candidate | 2 | — | — | — | — | — | — | — | — | NOT_RUN |
| candidate | 3 | — | — | — | — | — | — | — | — | NOT_RUN |

## Comparison controls

| Parameter | Unit | Required | Observed |
| --- | --- | --- | --- |
| Changed variables | count | 1 | — |
| Baseline independent runs | count | 3 | — |
| Candidate independent runs | count | 3 | — |
| Frozen R | iterations/s | — | — |
| Fixture seed | integer | — | — |
| Warm-up minimum | s | 120 | — |
| Baseline measurement | s | 600 | — |
| Read p95 diagnostic budget | ms | 300 | — |
| Write p95 diagnostic budget | ms | 800 | — |
| All-route p99 diagnostic budget | ms | 2000 | — |
| Unexpected failure diagnostic budget | % | <1 | — |
| p95 investigation delta | % | >10 | — |
| Owner-approved budget date | UTC date | — | — |
| Invariant failures | count | 0 | — |
| Dropped iterations at R | count | 0 | — |

## Per-operation before and after

| Variant | Run | Scenario ID | Operation | Samples | p50 ms | p95 ms | p99 ms | Offered iter/s | Completed iter/s | Orders/s | Unexpected % | Expected 409 | Dropped | Invariant failures |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| baseline | 1 | — | — | — | — | — | — | — | — | — | — | — | — | — |
| baseline | 2 | — | — | — | — | — | — | — | — | — | — | — | — | — |
| baseline | 3 | — | — | — | — | — | — | — | — | — | — | — | — | — |
| candidate | 1 | — | — | — | — | — | — | — | — | — | — | — | — | — |
| candidate | 2 | — | — | — | — | — | — | — | — | — | — | — | — | — |
| candidate | 3 | — | — | — | — | — | — | — | — | — | — | — | — | — |

## Cost transfer and validity

| Variant | Run | Generator CPU % | App CPU % | DB CPU % | Allocation B/op | GC pause total ms | Pool wait p95 ms | SQL p95 ms | Write p95 ms | Fixture stock units | Audit mismatch | Token expiry count |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| baseline | 1 | — | — | — | — | — | — | — | — | — | — | — |
| baseline | 2 | — | — | — | — | — | — | — | — | — | — | — |
| baseline | 3 | — | — | — | — | — | — | — | — | — | — | — |
| candidate | 1 | — | — | — | — | — | — | — | — | — | — | — |
| candidate | 2 | — | — | — | — | — | — | — | — | — | — | — |
| candidate | 3 | — | — | — | — | — | — | — | — | — | — | — |

## Range and delta

| Scenario ID | Operation | Baseline p95 min ms | Baseline p95 max ms | Candidate p95 min ms | Candidate p95 max ms | p95 delta % | Baseline spread % | Candidate spread % | Throughput delta % | Error delta pp | Allocation delta % | DB cost delta % |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| — | — | — | — | — | — | — | — | — | — | — | — | — |
