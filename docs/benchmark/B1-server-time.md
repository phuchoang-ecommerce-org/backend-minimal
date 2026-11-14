# B1 — Explain Server Time

## Repeat manifest

| Variant | Run | Run ID | Implementation SHA | Script SHA | Fixture seed | Migration | JDK | PostgreSQL | k6 | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| diagnostic | 1 | — | — | — | — | — | — | — | — | NOT_RUN |
| diagnostic | 2 | — | — | — | — | — | — | — | — | NOT_RUN |
| diagnostic | 3 | — | — | — | — | — | — | — | — | NOT_RUN |

## Trigger and control

| Field | Unit | Value |
| --- | --- | --- |
| Source scenario ID | ID | — |
| B0 baseline run ID | ID | — |
| Frozen offered rate | iterations/s | — |
| Fixture seed | integer | — |
| Measurement window | s | 600 |
| Independent repeats | count | 3 |
| Unexpected failure budget | % | <1 |
| Invariant failure budget | count | 0 |

## Aligned diagnostic windows

| Run | HTTP samples | HTTP p95 ms | HTTP p99 ms | App CPU % | Allocation MiB/s | Heap used MiB | GC pause p95 ms | GC pause total ms | Live threads | Pool active max | Pool pending max | Pool timeout count | Pool acquire p95 ms | SQL exec p95 ms | DB lock wait p95 ms | Unexpected % | Invariant failures | Clock offset ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 2 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| 3 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |

## Hypothesis measurements

| Run | CPU sampled % | Allocation sampled % | Pool wait share % | SQL share % | Lock wait share % | Unattributed share % |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | — | — | — | — | — | — |
| 2 | — | — | — | — | — | — |
| 3 | — | — | — | — | — | — |
