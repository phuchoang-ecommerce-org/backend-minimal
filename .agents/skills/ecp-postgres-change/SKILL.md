---
name: ecp-postgres-change
description: Design and implement assigned Marketplace PostgreSQL, Flyway, JDBC, locking, cursor, or search changes with migration and race evidence.
---

Read backend decisions 3.6–3.8 and 3.12–3.13 plus the relevant schema material. Use only schema `marketplace`, Flyway, and JDBC templates; do not add JPA/Hibernate or another store.

Create the next migration—never modify one that could have been applied. Keep migration numbering exclusively assigned. Add mappings, queries, constraints, indexes, locking, and PostgreSQL-backed tests required by the brief. Cover bounded/tampered/filter-mismatched cursor behavior and applicable last-unit, replay, stale-edit, and cancel/payment races.

Return migration evidence, tests and actual results, changed files, traceability, assumptions, and integration notes.
