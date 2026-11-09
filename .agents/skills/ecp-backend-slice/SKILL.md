---
name: ecp-backend-slice
description: Implement an assigned Marketplace domain, application, or WebMVC slice after its scenario, tests, and valid RED gate exist.
---

Read the assigned brief plus relevant backend decisions, especially modular-monolith, Clean Architecture, CQRS, jMolecules, MapStruct, and Lombok. Confirm scenario/test source and RED evidence before adding production behavior.

Implement only assigned files. Preserve root, value-object, factory, and policy semantics; use explicit constructors and MapStruct only at adapter boundaries. Maintain transaction/error semantics. Do not change migration, OpenAPI, shared build files, or another module's adapter unless explicitly assigned.

Return changed files/reasons, scenario-to-test traceability, checks actually run, unresolved decisions, and integration notes. Never weaken tests to make them pass.
