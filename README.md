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

2. Start PostgreSQL and export the environment values for the application:

   ```sh
   docker compose up -d --wait db
   set -a
   . ./.env
   set +a
   ```

   Compose reads `.env` automatically. Gradle does not, which is why the shell exports it above. Flyway creates and migrates the `marketplace` schema when the application starts.

3. Generate a local RSA key pair and export it in the formats expected by the JWT configuration:

   ```sh
   JWT_KEY_DIR=$(mktemp -d)
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$JWT_KEY_DIR/private.pem"
   export JWT_PRIVATE_KEY=$(openssl pkcs8 -topk8 -nocrypt -in "$JWT_KEY_DIR/private.pem" -outform DER | base64 | tr -d '\n')
   export JWT_PUBLIC_KEY=$(openssl pkey -in "$JWT_KEY_DIR/private.pem" -pubout -outform DER | base64 | tr -d '\n')
   ```

   Keep the private key and its environment value local. Repeat this step in a new shell session if you have not retained the key pair.

4. Start the API and check that it responds:

   ```sh
   SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
   ```

   In another terminal, run `curl http://localhost:8080/categories`. On a fresh database, the response has an empty `items` array because development fixtures are disabled by default. Stop the API with Ctrl-C; stop the database with `docker compose down` when finished.

## Verify and learn more

Run `./gradlew check` with Docker available to execute unit, integration, and checkout contract tests. The [disposable API rehearsal](docs/phase7-demo.md) has its own instructions and additionally requires Python 3 with PyYAML and `htpasswd`.

See the [approved project documentation](../docs-minimal/README.md) for business rules, architecture, and scope, and the [OpenAPI contract](../docs-minimal/SolutionArchitecture/api-contract/openapi.yaml) for target request and response shapes.
