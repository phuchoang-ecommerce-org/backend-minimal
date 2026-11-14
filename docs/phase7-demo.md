# Phase 7 disposable API rehearsal

This runner exercises the 18 implemented demo operation IDs from PB-002–010 over live HTTP. It creates a new PostgreSQL 18 container for each run, starts the existing API with the guarded `dev` fixture, adds a synthetic second Seller fixture, and removes its container in `finally`. It never connects to a supplied database. Account IDs and SellerShop IDs are distinct. Purchases, payments, cancellation, stock edits and account/address creation go through HTTP.

## Prerequisites and run

Use local Docker, Java 21, Python 3 with PyYAML, OpenSSL and `htpasswd`. The runner requires the named Spring Boot JAR built by this repository and reads the approved OpenAPI YAML for response and error schema assertions. No `.env` file is needed. Passwords, JWT signing keys and tokens are generated in memory and omitted from stdout.

```sh
rtk ./gradlew check jar bootJar --rerun-tasks --console=plain --no-daemon --continue
rtk python3 scripts/phase7_demo.py --runs 2 --verify-reports
```

The first command runs the PostgreSQL race/rollback suites and the production fixture guard. The second command checks all 18 operation IDs, prints status codes and a race/rollback report from JUnit XML, and includes the Git HEAD revision. Its output is safe to save as rehearsal evidence: it contains IDs and statuses but no credentials, JWTs or signing material. `--verify-reports` requires nonzero tests and zero failures, errors or skips in `test`, `integrationTest` and `checkoutRed` and the named race/rollback cases. Run the first command before recording a report so the XML is current.

Each run is its own reset. The script uses a random `marketplace-phase7-*` Docker container with `--rm`, a random mapped PostgreSQL port and random API port. On success or failure it stops the API and removes only that container. It does not call `flyway clean`, drop a shared database, or delete audit rows. If the process is interrupted before cleanup, inspect `docker ps --filter label=marketplace.phase7.disposable=true` and remove only a container whose `marketplace-phase7-*` name you recognize as this rehearsal.

The HTTP flow covers Guest browse/cart and one-time merge, registration/login, address create/list, two-Seller COD checkout, same-key replay and changed-body conflict, Member detail/history, Seller scoped lists and payments, paid cancellation rejection, a second order cancelled twice, foreign order masking, and stale/fresh stock version writes. Each recorded response is checked against its OpenAPI response schema and allowed error codes. The automated suites supply last-unit, same-key, cancel/payment, two-Seller and late-audit evidence. The sequential runner does not claim to reproduce those races.

Known scope: no configured valid voucher, payment gateway, review/image flow, or full 38-operation release. Only the 18 demo-relevant operations are asserted here. The local fixture uses one built-in dev Seller and one automatically provisioned second Seller; it does not change production seeding.
