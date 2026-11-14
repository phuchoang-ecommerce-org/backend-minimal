# Marketplace backend

This repository is the Java API for a marketplace. It serves Guest, Member, and Seller flows over HTTP and stores marketplace data in PostgreSQL. It is a modular monolith; the frontend is a separate project.

## What it supports

The current implementation covers the 18 operations used by the [backend API demo](docs/phase7-demo.md):

| User | Available capabilities |
| --- | --- |
| Guest | Browse categories and products, search and filter products, and manage a guest cart. |
| Member | Register, log in, merge a guest cart on login, manage addresses, place cash-on-delivery (COD) orders, view order history and details, and cancel eligible orders. |
| Seller | View owned products and orders, update stock with version checks, and record receipt of COD cash for owned orders. |

Checkout includes stock checks and idempotent order placement. Authentication uses signed JWTs with role and ownership checks. The approved [API contract](../docs-minimal/SolutionArchitecture/api-contract/index.md) describes the broader target; its full 38-operation scope is **not** implemented here. There is no payment gateway or shipping workflow.

## Built with

- **Java 21**, **Spring Boot 3.5** (WebMVC, Security, Validation), **Spring Modulith**, and **jMolecules**.
- **PostgreSQL 18** with Spring JDBC and **Flyway** migrations in the `marketplace` schema.
- **MapStruct** at adapter boundaries and **Lombok** for limited code generation.
- **Gradle 8.14.3** through the included wrapper; **JUnit 5**, **Mockito**, **AssertJ**, **ArchUnit**, Spring Boot Test, and **Testcontainers** for verification.

## Prerequisites

Install **JDK 21**, **Docker with Docker Compose**, and **OpenSSL**. You do not need to install Gradle or PostgreSQL separately: the Gradle wrapper downloads Gradle, and Compose runs PostgreSQL 18. Docker must be running before starting the database or running the PostgreSQL-backed tests.

## Start locally

Run these commands from this repository's root in a POSIX-compatible shell. The application defaults to the `prod` profile, so select `dev` explicitly for local use.

1. Create a local environment file:

   ```sh
   cp .env.example .env
   ```

   Edit `.env`: replace `DB_PASSWORD`, `DB_RUNTIME_PASSWORD`, and `DB_MIGRATION_PASSWORD` with the **same** local password, and replace `CURSOR_MAC_SECRET` with at least 32 random UTF-8 bytes. For example, `openssl rand -hex 32` prints a suitable secret. Keep `DEV_FIXTURES_ENABLED=false` for an empty local database. The `.env` file is ignored by Git.

2. Start PostgreSQL:

   ```sh
   docker compose up -d --wait db
   ```

   Compose reads `.env` automatically. The `dev` profile also loads `.env` from the repository root when the API starts. Exported environment variables take precedence. Flyway creates and migrates the `marketplace` schema when the application starts.

3. Generate a local RSA key pair in the formats expected by the JWT configuration:

   ```sh
   JWT_KEY_DIR=$(mktemp -d)
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$JWT_KEY_DIR/private.pem"
   openssl pkcs8 -topk8 -nocrypt -in "$JWT_KEY_DIR/private.pem" -outform DER | base64 | tr -d '\n'; printf '\n'
   openssl pkey -in "$JWT_KEY_DIR/private.pem" -pubout -outform DER | base64 | tr -d '\n'; printf '\n'
   ```

   Paste the first base64 line into `JWT_PRIVATE_KEY=` and the second into `JWT_PUBLIC_KEY=` in `.env`. Keep the private key and `.env` local. You can recreate `.env` from `.env.example` by repeating these steps; the template contains every required dev setting.

4. Start the API and check that it responds:

   ```sh
   SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
   ```

   In another terminal, run `curl http://localhost:8080/categories`. On a fresh database, the response has an empty `items` array because development fixtures are disabled by default. Stop the API with Ctrl-C; stop the database with `docker compose down` when finished.

## Verify and learn more

### Development seed accounts

Set `DEV_FIXTURES_ENABLED=true` in `.env` before starting the dev API, then run `node scripts/seed-dev.mjs` after it starts. Node 18 or newer is required. This calls the loopback-only `POST /dev/seed` endpoint and idempotently creates three accounts, one shop, five categories, 50 products and variants with stock, 50 linked product images, two addresses, 20 orders, and 20 reviews in a disposable local database. The image URLs point to five CC0 files on Wikimedia Commons; the database stores each file's source page and license URL, and browsers load images from `upload.wikimedia.org` when viewed. Run the script again to reset the three passwords without duplicating the generated rows. Copying `.env.example` alone does not create accounts; run the script before logging in.

| Role | Login | Password |
| --- | --- | --- |
| Member | `synthetic.member@example.test` | `DevMember2026!OnlyLocal` |
| Seller | `synthetic.seller@example.test` | `DevSeller2026!OnlyLocal` |
| Admin | `synthetic.admin@example.test` | `DevAdmin2026!OnlyLocal` |

These credentials are public development fixtures. The backend hashes the passwords and authenticates these accounts through normal `/auth/login`. The endpoint is absent in `prod`, and production rejects `DEV_FIXTURES_ENABLED=true`.

Run `./gradlew check` with Docker available to execute unit, integration, and checkout contract tests. The [disposable API rehearsal](docs/phase7-demo.md) has its own instructions and additionally requires Python 3 with PyYAML and `htpasswd`.

See the [approved project documentation](../docs-minimal/README.md) for business rules, architecture, and scope, and the [OpenAPI contract](../docs-minimal/SolutionArchitecture/api-contract/openapi.yaml) for target request and response shapes.
