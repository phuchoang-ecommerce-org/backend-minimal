# S2-07 / PB-019 rehearsal evidence

Date: 2026-11-14 (Asia/Ho_Chi_Minh). Frozen implementation revision: `e1284d7a6da51c29dff50e013f7d51c93db6084f`. Runner SHA-256: `48f02e3e8e6a8bc6da969380ec6990d058e3f755ae1c7d9583e908e13a7a1222`. Scenario SHA-256: `f6f01f86178cf7cf59f955401523ddb46a42b7dd58c2a4d7566618cab3e812ca`.

## Execution

| Gate | Actual result |
| --- | --- |
| `rtk ./gradlew check jar bootJar --rerun-tasks --console=plain --no-daemon --continue` | PASS; 13/13 tasks executed; both JARs built |
| Unit and architecture `test` | 33 tests, 0 failure/error/skip |
| PostgreSQL `integrationTest` | 55 tests, 0 failure/error/skip |
| PostgreSQL `checkoutRed` | 17 tests, 0 failure/error/skip |
| `rtk node validate-api.mjs` from `docs-minimal/util` | PASS; 38 operations, 10 use cases, 59 reachable schemas |
| `rtk python3 scripts/phase7_demo.py --runs 2 --verify-reports` | PASS twice; 18 operation IDs and 48 schema/HTTP assertions per run |
| Prod fixture guard | `ProdProfileIntegrationTest.prodStartsWithoutFixturesAndDoesNotExposeQuickLogin()` PASS |

The [raw HTTP and test output](phase7-rehearsal-output.txt) records response status codes and generated order IDs for both fresh runs. Each run started a uniquely named PostgreSQL 18 container, migrated and seeded it, used a different random API/DB port, and removed the container after completion. The output contains no password, JWT, signing key or credential hash. All demo mutations after synthetic fixture setup used HTTP.

Named PostgreSQL cases passed for last-unit stock, concurrent same-key replay, cancellation winning the root lock, payment winning the root lock, two Seller payments, checkout late-audit rollback and payment audit rollback. These are test-suite results; the sequential HTTP runner did not simulate races. Guest merge, owner masking, replay, changed-body conflict, paid cancellation, one-time stock restore and stale stock edit were observed through HTTP on each clean run.

The initial runner failed on a Guest cookie capture bug (`CookieJar` was treated as false while empty), then passed after the fix. The initial full build was blocked by a Gradle task scope error; moving the test report gate to the shared `Test` configuration restored Gradle compilation. Neither failure was labeled a behavioral RED. The final full build and both frozen-revision rehearsals passed.

Known scope: no valid voucher is configured. Payment gateway, review/image flow and the remaining 20 of 38 OpenAPI operations are outside this demo gate. The 18 operation IDs were validated on their demo-relevant responses and documented errors; this report does not claim a full release. No migration or production business behavior changed.
