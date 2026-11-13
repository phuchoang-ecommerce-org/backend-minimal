---
name: ecp-verify-change
description: Verify a Marketplace backend patch against its scenario, real execution path, DDD/module boundaries, CQRS ports, persistence and API/security contracts.
---

Read the brief, filled scenario, approved source documents and actual diff. For list/detail work, read the [Read Projection Boundary](../../../../docs-minimal/SolutionArchitecture/backend/2-application-architecture/2.2-application-architecture/2.1.2-application-architecture.md#read-projection-boundary) and [query scenarios](../../../../docs-minimal/SolutionArchitecture/backend/4-automated-testing/4.%20Automated%20Testing.md#http-database-and-profile-scenarios). Confirm the test source and RED evidence before implementation: compile, dependency, network, database-startup and zero-test failures are BLOCKED or NOT RUN, not behavioral RED. Distinguish a structural RED for a behavior-preserving refactor from a business-behavior RED.

Before handoff, inspect these failure modes in addition to running proportionate checks:

- The domain root/factory owns invariants, transitions and snapshots; adapters map and persist rather than decide business state.
- Every cross-module fact or mutation uses an exported owner-module API. Match each JDBC statement to its module's table ownership; Modulith package verification does not see SQL. Any foreign-table immutable projection has an explicit approved exception.
- WebMVC calls the actual input port/handler covered by application tests. No duplicate placeholder command path remains. Application dependencies point inward; provider `api` packages and module allowlists agree.
- Command and query handlers/ports are distinct. Input-port views do not expose outbound-port nested types. Read projections are immutable and owner/visibility filtered; query ports cannot write or load aggregates for ordinary reads.
- For list/detail reads, inspect the full OpenAPI response, query count at one item and maximum page size, and whether concurrent state changes can mix database snapshots within one response.
- Transaction, actor/role plus ownership, idempotency, lock/version, rollback, MapStruct, Lombok and API error behavior match the applicable contracts. Use real PostgreSQL evidence for relevant concurrency and late-failure paths.

Report PASS, FAIL, BLOCKED or NOT RUN for checks actually performed, with commands/results. Return scenario-to-test mapping, RED/GREEN evidence, severity-ranked findings, authorized changes, unresolved decisions and handoff recommendation. Do not alter behavior or expectations merely to make checks green.
