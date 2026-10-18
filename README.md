# Aegis

A Predictive, Safety-Constrained Resilience Control Framework for Distributed Microservices.

Aegis is a research/learning project. The synthetic checkout environment is a testbed for cascading failures and constrained resilience control, not a production commerce product. Predictive benefit remains a hypothesis; no performance or novelty result is claimed.

## Current state

M1.1 and M1.2-A are accepted. The user authorized M1.2-B followed by M1.2-C on 2026-10-18. The B foundation reactor build passed with four independent HTTP startup tests and executable JARs. Inventory C implementation passed verification and awaits user review; see [DECISIONS.md](docs/DECISIONS.md) for actual results. M1.2-D and later remain unauthorized.

Four Spring Boot foundations exist. Only Inventory has a business endpoint: `GET /api/v1/items/{sku}`. Checkout, Payment and Notification have no business endpoints yet. Reservation routes remain contract-only; no orchestration, reservation lifecycle, resilience policies, telemetry infrastructure, or Compose is implemented.

## Build and test

Prerequisites: JDK 21, access to Maven Central for the first dependency download, and a free Docker-compatible engine running Linux containers for Inventory PostgreSQL tests. No paid infrastructure or cloud account is required. The wrapper pins Maven 3.9.15 with distribution checksum verification; Boot 4.1.1 manages dependencies.

Commands run from the repository root in PowerShell (the isolated cache path shown is this workspace):

```powershell
$env:MAVEN_USER_HOME = 'D:\agies\.m2'
.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B verify
.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B -pl services/inventory-service -am test
```

Inventory tests use real PostgreSQL 17.11 in disposable Testcontainers containers, Flyway migrations, an opt-in synthetic catalog, and restricted runtime credentials. They fail rather than silently skip if Docker is unavailable. There is no H2 substitution. Test fixtures create a separate empty database solely to verify denied cross-database connection privileges.

Build artifacts are under `services/<module>/target/<module>-0.1.0-SNAPSHOT.jar`. Default ports: Checkout 8081, Inventory 8082, Payment 8083, Notification 8084. Foundation services start without databases; Inventory requires its own provisioned PostgreSQL database and credentials.

## Manual Inventory development

Provision `aegis_inventory` outside the application, with separate migrator/owner and runtime roles. Revoke PUBLIC database connection and schema-create privileges as appropriate; grant the runtime role CONNECT, schema USAGE and SELECT on `catalog_item` (and owner default SELECT privileges for future tables if desired). It must not own objects, write stock, create schemas, or connect to another service database. Credentials are supplied via `DB_URL`, `DB_USER`, `DB_PASSWORD`, `MIGRATION_USER`, and `MIGRATION_PASSWORD`; never commit secrets. Flyway migrates Inventory only, not databases or roles.

```powershell
java '-Duser.timezone=UTC' -jar services/inventory-service/target/inventory-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Use a UTC JVM for JDBC portability: this PostgreSQL image rejects the legacy host alias Asia/Calcutta sent by the JDBC driver. Tests pin user.timezone=UTC; this does not change user-facing scheduling timezone.

The explicit `local` profile adds a repeatable synthetic catalog fixture: AEGIS_ITEM (100 INR minor units, quantity 10) and AEGIS_EMPTY (250, quantity 0). It never overwrites existing rows. Default configuration runs only schema migrations and inserts no catalog data. No completed purchase/payment seed records exist. Stock is a point-in-time read, never an allocation promise. Full reproducible provisioning/Compose belongs to M1.4.

## Design and contracts

One PostgreSQL server with separate service databases/roles is the intended topology; it does not provide physical resource isolation. Independent service settings permit future resource limits. Business behavior remains separate from future telemetry, policies and inference. AWS requires separate approval and verified free usage eligibility.

- [Project specification](docs/PROJECT_SPEC.md)
- [Architecture and invariants](docs/ARCHITECTURE.md)
- [Exact six-phase, 25-milestone roadmap](docs/ROADMAP.md)
- [Decision register and verification](docs/DECISIONS.md)
- [Contributor instructions](AGENTS.md)
- OpenAPI: [Checkout](contracts/checkout-v1.json), [Inventory](contracts/inventory-v1.json), [Payment](contracts/payment-v1.json), [Notification](contracts/notification-v1.json)

Wire contracts remain unchanged. Their `not-implemented` labels record the contract-review snapshot; this README and verification record report current implementation status. Contract metadata changes require separate approval. Notification replay is designed (201 new, 200 same, 409 conflict) but its implementation remains M1.2-D.

## Local logs

Build logs live in `logs/`, which is excluded from Git along with Maven caches and build artifacts. To capture a test run in PowerShell, create the directory if needed and redirect output to `logs/inventory-tests.log`. Test reports remain under the Inventory module target/surefire-reports directory.


## Run only the PostgreSQL database tests

Start Docker Desktop in Linux-container mode and wait for `docker version` to show both Client and Server. From D:\agies in PowerShell:

```powershell
$env:MAVEN_USER_HOME = 'D:\agies\.m2'
New-Item -ItemType Directory -Force logs | Out-Null
.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -pl services/inventory-service -am '-Dtest=InventoryApplicationTest' test 2>&1 | Tee-Object -FilePath logs/postgres-tests.log
```

Expect 15 tests with zero failures/errors/skips and BUILD SUCCESS. Testcontainers creates PostgreSQL, Flyway applies the schema and local fixture, and tests exercise real HTTP catalog reads, invalid rows, migration validation and restricted-role isolation. Containers are cleaned up automatically. No manual database, credentials, Compose, or pre-created catalog is needed for this test command. First execution may download Maven dependencies and Docker images. XML/text reports are in `services/inventory-service/target/surefire-reports/`. To run all Inventory unit/API/database tests, omit the `-Dtest=InventoryApplicationTest` argument.
