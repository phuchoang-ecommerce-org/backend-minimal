# B4 — Proven Hotspot

## Repeat manifest

| Variant | Run | Run ID | Implementation SHA | Script SHA | Fixture seed | Migration | JDK | PostgreSQL | k6 | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| baseline | 1 | — | — | — | — | — | — | — | — | NOT_RUN |
| baseline | 2 | — | — | — | — | — | — | — | — | NOT_RUN |
| baseline | 3 | — | — | — | — | — | — | — | — | NOT_RUN |
| candidate | 1 | — | — | — | — | — | — | — | — | NOT_RUN |
| candidate | 2 | — | — | — | — | — | — | — | — | NOT_RUN |
| candidate | 3 | — | — | — | — | — | — | — | — | NOT_RUN |

## Profiling and harness settings

| Parameter | Unit | Planned | Observed |
| --- | --- | --- | --- |
| JFR sample minimum | s | 60 | — |
| JFR sample maximum | s | 120 | — |
| JMH forks | count | 3 | — |
| JMH warm-up iterations | count | 5 | — |
| JMH warm-up iteration | s | 1 | — |
| JMH measurement iterations | count | 10 | — |
| JMH measurement iteration | s | 1 | — |
| Input sizes | lines | 1, 10, 100 | — |
| Functional equivalence failures | count | 0 | — |
| Frozen API R | iterations/s | — | — |

## JFR hotspot evidence

| Run | Scenario ID | Sample s | HTTP p95 ms | CPU samples | Hot-method CPU % | Allocation MiB/s | Hot-method allocation % | GC pause total ms | Monitor blocked ms | DB wait ms | Profiler overhead % |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | — | — | — | — | — | — | — | — | — | — | — |
| 2 | — | — | — | — | — | — | — | — | — | — | — |
| 3 | — | — | — | — | — | — | — | — | — | — | — |

## JMH comparison

| Variant | Input lines | Run | Score ns/op | Score error ns/op | Throughput ops/s | Allocation B/op | GC count | GC time ms | Threads | State scope |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| baseline | 1 | 1 | — | — | — | — | — | — | — | — |
| baseline | 1 | 2 | — | — | — | — | — | — | — | — |
| baseline | 1 | 3 | — | — | — | — | — | — | — | — |
| baseline | 10 | 1 | — | — | — | — | — | — | — | — |
| baseline | 10 | 2 | — | — | — | — | — | — | — | — |
| baseline | 10 | 3 | — | — | — | — | — | — | — | — |
| baseline | 100 | 1 | — | — | — | — | — | — | — | — |
| baseline | 100 | 2 | — | — | — | — | — | — | — | — |
| baseline | 100 | 3 | — | — | — | — | — | — | — | — |
| candidate | 1 | 1 | — | — | — | — | — | — | — | — |
| candidate | 1 | 2 | — | — | — | — | — | — | — | — |
| candidate | 1 | 3 | — | — | — | — | — | — | — | — |
| candidate | 10 | 1 | — | — | — | — | — | — | — | — |
| candidate | 10 | 2 | — | — | — | — | — | — | — | — |
| candidate | 10 | 3 | — | — | — | — | — | — | — | — |
| candidate | 100 | 1 | — | — | — | — | — | — | — | — |
| candidate | 100 | 2 | — | — | — | — | — | — | — | — |
| candidate | 100 | 3 | — | — | — | — | — | — | — | — |

## Original API comparison

| Variant | Run | Samples | p50 ms | p95 ms | p99 ms | Completed iter/s | Unexpected % | Invariant failures | Allocation B/op | SQL p95 ms | Pool wait p95 ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| baseline | 1 | — | — | — | — | — | — | — | — | — | — |
| baseline | 2 | — | — | — | — | — | — | — | — | — | — |
| baseline | 3 | — | — | — | — | — | — | — | — | — | — |
| candidate | 1 | — | — | — | — | — | — | — | — | — | — |
| candidate | 2 | — | — | — | — | — | — | — | — | — | — |
| candidate | 3 | — | — | — | — | — | — | — | — | — | — |
