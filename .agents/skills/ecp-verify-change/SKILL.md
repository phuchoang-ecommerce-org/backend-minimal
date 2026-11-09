---
name: ecp-verify-change
description: Verify a Marketplace backend patch or handoff against its scenario, test-first evidence, architecture, persistence, and API/security contracts.
---

Read the brief, filled scenario, source documents, and actual diff. Before implementation, verify test source and behavioral RED; compile, dependency, network, database-startup, or zero-test failures are BLOCKED or NOT RUN—not RED.

After implementation, run proportionate checks and report PASS, FAIL, BLOCKED, or NOT RUN with commands/results. Inspect contract drift, dependency direction, mapper boundary, forbidden Lombok, unauthorized policy, ownership, idempotency/race, and migration evidence. Do not alter behavior or expectations merely to make checks green.

Return scenario-to-test mapping, RED/GREEN evidence, actual checks, severity-ranked findings, authorized changes, unresolved decisions, and handoff recommendation.
