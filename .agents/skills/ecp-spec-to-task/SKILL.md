---
name: ecp-spec-to-task
description: Turn an approved Marketplace backend feature or fix into a traceable task brief and test-first scenario before implementation.
---

Use before changing business behavior. Read the applicable BA use case/rules in `../docs-minimal/BusinessAnalyst/`, relevant architecture in `../docs-minimal/SolutionArchitecture/backend/`, and API contract when HTTP is involved.

Produce a brief before code: UC/BR/ARCH IDs; document revision or content hash; decisions/conflicts; explicit file scope; port/DTO and MapStruct boundary; expected result/error; unresolved questions. Fill the scenario template in `../docs-minimal/SolutionArchitecture/backend/4-automated-testing/4. Automated Testing.md` with stable IDs, Given/When/Then, persistence and forbidden effects, fixtures, cleanup, and relevant concurrency behavior.

Do not infer unapproved policy from a title or authorize implementation. The task is ready only with scenario, test source, and valid behavioral RED evidence.
