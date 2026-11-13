# Marketplace backend agent guide

> [!NOTE]
> The approved documentation source is [Document](../docs-minimal). Do not copy it into skills.

## Scope and source baseline

This repository is the implementation root. Read the relevant source before changing behavior:

- Business scope, rules, and use cases: `../docs-minimal/BusinessAnalyst/`.
- API contracts: `../docs-minimal/SolutionArchitecture/api-contract/`.
- Architecture and technical decisions: `../docs-minimal/SolutionArchitecture/backend/`.
- Runtime/security profiles: `../docs-minimal/SolutionArchitecture/system/`.
- Priorities and demos: `../docs-minimal/ProductManager/`.

The supported stack is Java 21, Spring Boot WebMVC, Spring Modulith, jMolecules, PostgreSQL, Flyway, `JdbcTemplate`/`NamedParameterJdbcTemplate`, MapStruct, Lombok, JUnit 5, Mockito, AssertJ, ArchUnit, and Spring Boot Test. Preserve the modular-monolith/Clean-Architecture/CQRS design. Do not introduce JPA/Hibernate, WebFlux, Kafka, Redis, MongoDB, Elasticsearch, external identity, payment, shipping, storage, or email/SMS integrations without an approved specification change.

Use only schema `marketplace`; PostgreSQL is the persistent store. Keep MapStruct at adapter boundaries; it must not create or mutate aggregates or map actor/role claims from requests. Use explicit constructors—Lombok constructor annotations, `Data`, `Value`, `Builder`, and `SuperBuilder` are forbidden.

## Required workflow

- For a business feature or fix, use `$ecp-spec-to-task` before implementation.
- For backend business implementation, use `$ecp-backend-slice` to build domain behavior, owner-module APIs, command/query ports, and adapters in dependency order. For list/detail queries, read the Read Projection Boundary in `../docs-minimal/SolutionArchitecture/backend/2-application-architecture/2.2-application-architecture/2.1.2-application-architecture.md`.
- Write the scenario and automated test source before production behavior. A valid RED result is a failing behavioral assertion—not a compile, dependency, network, or zero-test failure. Do not begin dependent implementation until the RED evidence is recorded.
- Use `$ecp-postgres-change` for SQL, Flyway, JDBC, locking, cursor, or search work; never edit an applied migration.
- Use `$ecp-api-security-contract` for HTTP, JWT, authorization, DTO, error, cursor, or expected-version changes.
- Use `$ecp-platform-bootstrap` for build, dependency, Docker Compose, CI, or profile changes.
- Use `$ecp-verify-change` before handing off a code or configuration change.

Do not treat an Account ID as a SellerShop ID. Enforce JWT signature/issuer/audience/expiry, RBAC plus ownership, and the documented 401/403/404 behavior. Keep checkout atomic and idempotent; test last-unit, same-key replay, stale edit, cancel/payment race, and ownership boundaries when applicable. Do not deploy or merge unless explicitly asked.

## Coordination

The coordinator owns integration and shared build/configuration. Assign agents explicit write paths; reviewers are read-only unless given a specific follow-up. Do not have two agents write the same file or lockfile. The standard handoff records changed files, scenario-to-test traceability, RED/GREEN evidence, actual test results, unresolved decisions, and integration notes.
