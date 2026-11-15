# B0 benchmark scripts

`b0.js` implements the initial B0 slice from the approved benchmark plan:

- BM-01 discovery variants: categories, product browse/filter/search, cursor limits 20/100, and product detail.
- BM-04 two-Seller COD checkout: refill a worker-owned cart, checkout, read the order, and verify cart consumption.
- BM-05 idempotency: same-key/same-body replay and exact `IDEMPOTENCY_CONFLICT` for a changed body.

The k6 workload does not seed or reset a database. `generate-b0-fixture.mjs` creates a guarded seed SQL file and the matching local worker fixture. B0 may run only after the real PostgreSQL correctness gate is GREEN, against a named disposable database with a frozen revision and fixed resource limits. The latency/error budgets in `../B0-baseline.md` remain diagnostic hypotheses; only assertions, invariant failures, and dropped iterations fail the k6 process.

## Prerequisites

1. Install and pin a local k6 version. Record it in the run manifest with `k6 version`.
2. Start the frozen backend revision against a disposable PostgreSQL database. Do not use production or shared data.
3. Load a deterministic fixture with more than 100 visible products, enough stock for warm-up plus all measured checkouts, and known common/rare/no-result search terms. It must contain at least two Sellers with distinct Account and Shop IDs and one exclusive Member/cart/address per possible VU.
4. Run the PostgreSQL functional correctness suite and record the report path before B0.
5. Generate and load the deterministic fixture as described below. Never commit the generated credentials, SQL, or JWTs.

## Generate the large-scale fixture

From the repository root, run:

```bash
node docs/benchmark/scripts/generate-b0-fixture.mjs
```

This writes ignored, mode-`0600` local artifacts:

- `docs/benchmark/scripts/generated/b0-fixture.json` — public query values and 1,000 exclusive Member workers for k6.
- `docs/benchmark/scripts/generated/b0-seed.sql` — set-based PostgreSQL inserts for 1,000 Members, 100 Sellers, 20 categories, 10,000 products, 20,000 variants, 50,000 historical orders, and 100,000 order lines.

The default target is exactly `marketplace_benchmark_b0`. The SQL aborts if the connected database has another name, migrations are absent, or Marketplace data already exists. It contains no `TRUNCATE`; recreate the disposable database between independent repeats.

### Create the disposable database

Docker Compose creates a database named `marketplace` by default. Do not use that database for B0. Start PostgreSQL and create the separate benchmark database:

```bash
docker compose up -d db

docker compose exec -T db \
  createdb -U marketplace_dev marketplace_benchmark_b0
```

If `createdb` reports that the database already exists, do not silently reuse it. Confirm that it is an intentionally preserved, empty B0 database; otherwise recreate only that named disposable database before continuing. Never reset the default `marketplace` database.

### Apply Flyway migrations and start the backend

Stop any backend process currently using port 8080. In the terminal that starts the backend, override `DB_URL` so both the runtime DataSource and Flyway use the benchmark database:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/marketplace_benchmark_b0
export DB_RUNTIME_USER=marketplace_dev
export DB_MIGRATION_USER=marketplace_dev

# Keep DB_RUNTIME_PASSWORD, DB_MIGRATION_PASSWORD, JWT keys, and other
# required runtime variables set to their existing local values.
SPRING_PROFILES_ACTIVES=dev ./gradlew bootRun
```

Wait until the application reports that startup completed. Flyway must finish before loading the seed. Keep this backend process running with the same `DB_URL`; restarting it later without the override reconnects it to the wrong database.

### Load the generated fixture

In another terminal, load the SQL through the PostgreSQL container. Host `psql` is not required:

```bash
docker compose exec -T db \
  psql -U marketplace_dev -d marketplace_benchmark_b0 \
  < docs/benchmark/scripts/generated/b0-seed.sql
```

The command must finish with `COMMIT`. Verify that the first synthetic Member exists:

```bash
docker compose exec -T db \
  psql -U marketplace_dev -d marketplace_benchmark_b0 -At \
  -c "SELECT count(*) FROM marketplace.account WHERE normalized_login='b0.member.000001@example.test'"
```

The expected output is `1`.

### Verify login before running k6

With the backend still connected to `marketplace_benchmark_b0`, verify the exact account used by worker 1:

```bash
curl -i http://127.0.0.1:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "login":"b0.member.000001@example.test",
    "password":"B0SyntheticOnly2026!"
  }'
```

Continue only after this returns HTTP 200. HTTP 401 means either the fixture was not loaded into `marketplace_benchmark_b0`, the backend was launched against another `DB_URL`, or custom fixture credentials do not match. Treat a k6 setup failure such as `Fixture login failed for worker 1; HTTP 401` as `BLOCKED`, not as benchmark evidence.

The generated accounts share a known synthetic-only password and must never be exposed outside the isolated benchmark environment. To replace it, set both `B0_FIXTURE_PASSWORD` and a matching BCrypt `B0_FIXTURE_PASSWORD_HASH` before running the generator. Run `node --test docs/benchmark/scripts/generate-b0-fixture.test.mjs` after changing generator behavior.

Each worker's two variants belong to different Seller Shops. The runner logs in during k6 `setup()`, outside the measured business steps. The script never prints tokens or response bodies.

## 1. Smoke the frozen build

Create an evidence directory first because k6 cannot create it from `handleSummary`:

```bash
export BASE_URL=http://127.0.0.1:8080
export FIXTURE_FILE="$PWD/docs/benchmark/scripts/generated/b0-fixture.json"
export RUN_ID=b0-smoke-20261123T010000Z
export RESULTS_DIR="$PWD/docs/benchmark/results/$RUN_ID"
mkdir -p "$RESULTS_DIR"

k6 run \
  -e BASE_URL="$BASE_URL" \
  -e FIXTURE_FILE="$FIXTURE_FILE" \
  -e RUN_ID="$RUN_ID" \
  -e RESULTS_DIR="$RESULTS_DIR" \
  -e PROFILE=smoke \
  -e WORKLOAD=combined \
  -e MAX_VUS=1 \
  --out "json=$RESULTS_DIR/raw.json" \
  docs/benchmark/scripts/b0.js
```

Smoke is a setup check, not a benchmark result. If it fails, mark the attempted run `BLOCKED`; do not record zeros or performance conclusions.

## 2. Calibrate discovery and checkout separately

Run the 1/5/10 iterations-per-second ladder for two minutes per rate. Use a fresh fixture state before checkout calibration and allocate at least `MAX_VUS` workers; the script aborts instead of wrapping an exhausted identity pool.

```bash
for workload in discovery checkout; do
  for rate in 1 5 10; do
    export RUN_ID="b0-cal-${workload}-${rate}-20261123T020000Z"
    export RESULTS_DIR="$PWD/docs/benchmark/results/$RUN_ID"
    mkdir -p "$RESULTS_DIR"

    k6 run \
      -e BASE_URL="$BASE_URL" \
      -e FIXTURE_FILE="$FIXTURE_FILE" \
      -e RUN_ID="$RUN_ID" \
      -e RESULTS_DIR="$RESULTS_DIR" \
      -e PROFILE=calibration \
      -e WORKLOAD="$workload" \
      -e RATE="$rate" \
      -e PREALLOCATED_VUS="$rate" \
      -e MAX_VUS="$rate" \
      --out "json=$RESULTS_DIR/raw.json" \
      docs/benchmark/scripts/b0.js
  done
done
```

Reset the disposable database to the same fixed fixture before every checkout calibration rate. The example uses one VU per offered iteration/s only as a starting value; increase `PREALLOCATED_VUS`/`MAX_VUS` if the generator needs more concurrency, then restart the ladder and record the change. Never infer server capacity from a run with dropped iterations or a saturated generator.

Choose and record frozen `R` only after reviewing achieved rate, dropped iterations, assertions, generator headroom, and fixture capacity. Do not lower `R` later merely to make a candidate pass.

## 3. Run three independent baselines

Before each repeat:

- recreate only the named disposable database and load the exact same fixture;
- confirm app/DB/generator limits, migration revision, shipped 12- or 18-operation target, and cache class;
- create `benchmark/results/<run-id>/manifest.yaml` from the template in the approved benchmark documentation;
- set `RATE` to the frozen combined offered rate and record `CHECKOUT_PERCENT` as part of the workload definition.

The command below performs an excluded two-minute warm-up followed by a ten-minute measurement. Custom B0 trends contain measurement samples only; raw k6 data retains the `stage` tag so warm-up and measurement can be separated.

```bash
export FROZEN_R=5
export CHECKOUT_PERCENT=20
export PREALLOCATED_VUS=5
export MAX_VUS=10

for repeat in 1 2 3; do
  export RUN_ID="b0-run-${repeat}-20261123T030000Z"
  export RESULTS_DIR="$PWD/docs/benchmark/results/$RUN_ID"
  mkdir -p "$RESULTS_DIR"

  k6 run \
    -e BASE_URL="$BASE_URL" \
    -e FIXTURE_FILE="$FIXTURE_FILE" \
    -e RUN_ID="$RUN_ID" \
    -e RESULTS_DIR="$RESULTS_DIR" \
    -e PROFILE=baseline \
    -e WORKLOAD=combined \
    -e RATE="$FROZEN_R" \
    -e CHECKOUT_PERCENT="$CHECKOUT_PERCENT" \
    -e PREALLOCATED_VUS="$PREALLOCATED_VUS" \
    -e MAX_VUS="$MAX_VUS" \
    -e WARMUP_DURATION=2m \
    -e MEASUREMENT_DURATION=10m \
    -e REQUEST_TIMEOUT=5s \
    --out "json=$RESULTS_DIR/raw.json" \
    docs/benchmark/scripts/b0.js

  # Stop here. Reset and validate the disposable fixture before the next repeat.
done
```

Do not run the loop unattended without inserting the fixture reset and pre-run validation between repeats. Prefer executing one repeat at a time and replacing the timestamp per run ID.

## 4. Validate and record evidence

Preserve `raw.json`, `summary.json`, the manifest, sanitized resource signals, and checksums under each run directory. Then run independent PostgreSQL reconciliation for created orders, duplicate orders, stock delta, consumed carts, Seller portions, totals/snapshots, audit rows, and replay snapshots.

The k6 assertions cover response-level invariants only. A run cannot be valid or PASS until PostgreSQL reconciliation succeeds. A timed-out request may still have committed, so reconcile by command/idempotency identity rather than HTTP success count alone.

Fill one row per operation, variant, run, and stage in `../B0-baseline.md`. Do not average percentiles across routes or repeats. Use `NOT_RUN` for unexecuted work, `BLOCKED` for invalid setup, `INCONCLUSIVE` for inadequate samples, and `FAIL` for a valid measured failure.

## Script variables

| Variable | Default | Meaning |
| --- | --- | --- |
| `BASE_URL` | `http://127.0.0.1:8080` | Frozen backend base URL |
| `FIXTURE_FILE` | `./generated/b0-fixture.json` | Generated, ignored fixture metadata/credentials |
| `PROFILE` | `smoke` | `smoke`, `calibration`, or `baseline` |
| `WORKLOAD` | `combined` | `discovery`, `checkout`, or `combined` |
| `RATE` | `1` | Offered iterations/s |
| `CHECKOUT_PERCENT` | `20` | Percent of combined iterations assigned to checkout |
| `PREALLOCATED_VUS` | `max(2, RATE)` | Initial arrival-rate VUs |
| `MAX_VUS` | `max(2, 2 × RATE)` | VU/worker ceiling; checkout needs this many workers |
| `WARMUP_DURATION` | `2m` | Excluded baseline warm-up |
| `MEASUREMENT_DURATION` | `10m` | Baseline measurement window |
| `CALIBRATION_DURATION` | `2m` | Calibration window per rate |
| `REQUEST_TIMEOUT` | `5s` | Per-request timeout |
| `RUN_ID` | `b0-unidentified` | Evidence identity and metric tag |
| `RESULTS_DIR` | `.` | Existing directory for `summary.json` |
