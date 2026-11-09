---
name: ecp-platform-bootstrap
description: Bootstrap or change Marketplace backend build, dependency, local runtime, CI, or dev/prod profile configuration without deploying.
---

Read backend decisions 3.2, 3.3, 3.10, and 3.11 plus `../docs-minimal/SolutionArchitecture/system/runtime-profiles.md` and `security.md`. Inventory existing files and dependency versions before edits; do not replace scaffold behavior without evidence.

Choose and pin a compatible Java 21/Spring Boot/Modulith/jMolecules/Gradle/JUnit 5/MapStruct/Lombok matrix. Configure annotation processors and Lombok policy. Keep secrets out of Git. Validate Compose, startup/migration, required compile/tests, and that production lacks quick login/seed behavior. Do not upgrade versions during a business slice or deploy automatically.

Return the version matrix, files changed, actual check results, blocked requirements, and rollback/integration notes.
