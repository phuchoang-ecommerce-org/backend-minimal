---
name: ecp-benchmark-evidence
description: Plan, run, or review phased Marketplace backend benchmarks and fill numerical evidence matrices for measured API, SQL, saturation, and hotspot results.
---

# Backend benchmark evidence

Use `../docs-minimal/SolutionArchitecture/backend/5-benchmark/` as the approved source for phase gates, scenarios, methods, and run records. Keep the numerical matrices in `docs/benchmark/`. Use only operations implemented in the frozen backend revision; record omitted operations and their dependencies. Do not turn unmeasured cells into zero or PASS.

Before B0 execution, confirm real PostgreSQL correctness evidence, a disposable database, synthetic identities, a fixed fixture, resource limits, and a frozen revision. Record the shipped 18-operation target or 12-operation fallback. Scope the initial six-hour B0 slice to BM-01, BM-04, and BM-05; expand only after evidence and capacity are reviewed. Treat the stated latency/error budgets as diagnostic hypotheses until the owner approves them as release gates.

For each run, keep a manifest and sanitized raw evidence under `benchmark/results/<run-id>/`; keep executable workloads under `benchmark/`. Fill one row per operation, variant, run, and stage in the relevant matrix. Preserve separate offered and achieved rates, samples and p50/p95/p99 per run, expected conflicts, unexpected failures, dropped iterations, generator headroom, persisted invariant counts, and resource signals. Do not average percentiles across runs or routes. Mark unexecuted rows `NOT_RUN`, invalid setup `BLOCKED`, insufficient samples `INCONCLUSIVE`, and valid failures `FAIL`. A setup error is never a benchmark result.

Choose the next phase from measured evidence: B1 for unexplained server time; B2 for sustained load and SQL cost; B3 for repeatable saturation/recovery questions; B4 only for a proven Java hotspot; B5 after repeated before/after evidence and functional tests. SQL investigation and JFR may skip earlier optional monitoring tools. Keep management endpoints private and bounded, and stop overload experiments at the documented laboratory limits. Never run destructive fixtures or stress/spike on production or shared data.

When a code or configuration change is required, coordinate exclusive file ownership, use the applicable existing backend skills, and verify the change before handoff. Report run IDs, source revisions, scenario coverage, raw evidence locations, matrix rows filled, actual test outcomes, and unresolved decisions.
