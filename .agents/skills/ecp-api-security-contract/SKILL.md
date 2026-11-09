---
name: ecp-api-security-contract
description: Implement or review assigned Marketplace HTTP, JWT, authorization, DTO, error, cursor, and expected-version contracts against approved API documentation.
---

Read `../docs-minimal/SolutionArchitecture/api-contract/index.md`, `openapi.yaml`, authorization matrix, exceptional errors, HTTP inventory, JWT, and runtime guidance. Work only within approved UC-01–UC-10/inventory scope.

Preserve HTTP/JSON, `problem+json`, cursor, expected-version, generated-type, and Guest-cart Origin/CSRF contracts. Verify JWT signature, issuer, audience, and expiry; authorize by both RBAC and ownership. A role never replaces ownership and Account ID never substitutes for SellerShop ID. Cover 401/403/404, role, and ownership cases.

Review is read-only unless assigned a write scope. Report contract gaps, test evidence, authorized changes, and unresolved decisions.
