# Aegis

A Predictive, Safety-Constrained Resilience Control Framework for Distributed Microservices.

Aegis is a research/learning project. The synthetic checkout environment is a testbed for cascading failures and constrained resilience control, not a production commerce product. Predictive benefit remains a hypothesis; no performance or novelty result is claimed.

## Current state

M1.1 and M1.2-A/B/C/D are accepted for progression. Checkout E is implemented and verified, awaiting user acceptance. Work is on `feature/m1.2-e-checkout`, uncommitted for review. M1.2-F and later remain unauthorized. See [DECISIONS.md](docs/DECISIONS.md) for actual commands and results.

Implemented endpoints:

| Service | Endpoint | Behavior |
|---|---|---|
| Checkout | GET `/api/v1/purchases/{purchaseId}` | Read durable local purchase snapshot; no workflow advancement |
| Inventory | GET `/api/v1/items/{sku}` | Authoritative catalog/stock snapshot |
| Payment Simulator | GET `/api/v1/payments/{paymentId}` | Read stored PENDING/SUCCEEDED/DECLINED outcome |
| Notification | POST `/api/v1/notifications` | 201 new; 200 identical replay; 409 conflicting identity |
| Notification | GET `/api/v1/notifications/{notificationId}` | Read synthetic stored receipt |

No charge/refund, reservation, orchestration, resilience policy, delivery worker, telemetry stack, or Compose behavior is implemented. STORED means a local receipt, never actual email delivery or authoritative purchase confirmation.

## Build and test

Prerequisites: JDK 21, access to Maven Central for the first dependency download, and a free Docker-compatible engine running Linux containers for service PostgreSQL tests. No paid infrastructure or cloud account is required. The wrapper pins Maven 3.9.15 with distribution checksum verification; Boot 4.1.1 manages dependencies.

Commands run from the repository root in PowerShell (the isolated cache path shown is this workspace):

```powershell
$env:MAVEN_USER_HOME = 'D:\agies\.m2'
.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B verify
.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B -pl services/inventory-service -am test
```

Inventory tests use real PostgreSQL 17.11 in disposable Testcontainers containers, Flyway migrations, an opt-in synthetic catalog, and restricted runtime credentials. They fail rather than silently skip if Docker is unavailable. There is no H2 substitution. Test fixtures create a separate empty database solely to verify denied cross-database connection privileges.

Build artifacts are under `services/<module>/target/<module>-0.1.0-SNAPSHOT.jar`. Default ports: Checkout 8081, Inventory 8082, Payment 8083, Notification 8084. All four services require their own provisioned database and credentials for manual runs.

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

Wire contracts remain unchanged. Their `not-implemented` labels record the contract-review snapshot; this README and verification record report current implementation status. Contract metadata changes require separate approval. Notification receiver replay (201 new, 200 same, 409 conflict) is implemented in accepted M1.2-D.

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


## Payment and Notification development

Use separate databases/roles; no service connects to another service database. Schema provisioning is manual until M1.4. Configure each process independently with `DB_URL`, `DB_USER`, `DB_PASSWORD`, `MIGRATION_USER`, `MIGRATION_PASSWORD`, and optional `PORT`. Defaults identify service-local databases but contain no passwords. Flyway uses the migration owner; runtime credentials must be restricted.

| Service | Database / default app role | Required runtime privileges |
|---|---|---|
| Payment | `aegis_payment` / `aegis_payment_app` | CONNECT, schema USAGE, SELECT on payment only |
| Notification | `aegis_notification` / `aegis_notification_app` | CONNECT, schema USAGE, SELECT on notification_receipt; INSERT on notification_id, purchase_id, kind only |

Revoke PUBLIC connection/schema-create privileges appropriately. Runtime roles must not own objects, update/delete receipts, create tables, or connect to sibling databases. Provision/grant privileges with an administrator outside the service; migrations create only service-owned objects. No runtime credentials are embedded. Payment migrations insert **no outcomes**; its records exist only in isolated test fixtures. Notification has no seed data or workers.

After setting each service's environment and building:

```powershell
java '-Duser.timezone=UTC' -jar services/payment-simulator/target/payment-simulator-0.1.0-SNAPSHOT.jar
java '-Duser.timezone=UTC' -jar services/notification-service/target/notification-service-0.1.0-SNAPSHOT.jar
```

Run these in separate terminals/processes with their own credentials. Default ports are 8083/8084. The APIs are synthetic local-development surfaces; no authentication platform or public deployment is introduced.

Notification uses a short READ COMMITTED transaction: INSERT ON CONFLICT DO NOTHING RETURNING, then a separate SELECT if a competing identity exists. Compare parsed purchase UUID/kind, preserving the original receipt/time. The transaction proxy commits before the controller returns success. No SELECT-before-INSERT race, JVM lock/cache, overwrite, or caught uniqueness error followed by queries in an aborted transaction is used. A timeout/unavailable response does not prove an earlier write failed; retry with the same identity.

### D verification

From the repository root, with Docker running:

```powershell
$env:MAVEN_USER_HOME = 'D:\agies\.m2'
.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B -pl services/payment-simulator,services/notification-service -am test
.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B verify
```

D full verification (historical): **105 tests, zero failures/errors/skips**. Payment 34, Notification 39, Inventory regression 31, Checkout startup 1. Flyway validation, real PostgreSQL constraints/role isolation, database outages, two-instance duplicate races, waiting behind uncommitted rows, and application-context restart were tested. Reports are under each module's `target/surefire-reports`; logs are ignored under `logs/m1.2-d-*.log`. Testcontainers provisions and cleans disposable environments; no manual database is needed for automated tests.

The two Notification instances have independent Spring contexts/HTTP servers/pools in one test JVM. Restart closes/recreates one full application context while preserving PostgreSQL; this is not an OS-process crash or complete deployment restart experiment. Full process/deployment reproducibility remains M1.4. Dedicated full OpenAPI validation remains unavailable; tests check response fields against the authoritative contracts plus statuses, enums, nulls and errors. Contracts remain unchanged.

## Checkout development (M1.2-E)

GET `/api/v1/purchases/{purchaseId}` reads the complete local purchase snapshot. Strict UUID validation returns 400 INVALID_REQUEST; missing identity returns 404 PURCHASE_NOT_FOUND. Storage loss/timeouts return 503 SERVICE_UNAVAILABLE; unexpected failures return sanitized 500 INTERNAL_ERROR. Missing state never becomes a failed or successful purchase. All eleven contract properties are returned; amountMinor/currency remain explicit nulls until established together. GET performs no state updates or remote lookups. Purchase POST is absent.

Provision `aegis_checkout` and separate `aegis_checkout_migrator`/`aegis_checkout_app` credentials manually, outside the application. The migrator owns its schema/objects; runtime grants are CONNECT on that database, USAGE on its schema, and SELECT on purchase only. Revoke PUBLIC connection/schema-create privileges and cross-database access; grant no INSERT/UPDATE/DELETE/TRUNCATE/DDL or migration-history access to the runtime role. Before initial migration, the owner may set default SELECT privileges for this schema, then narrow them to purchase after migration. Tests grant SELECT only on purchase after owner migration. No grants or roles are automatically provisioned in application migrations.

Set `DB_URL` (default jdbc:postgresql://localhost:5432/aegis_checkout), `DB_USER` (default aegis_checkout_app), `DB_PASSWORD`, `MIGRATION_USER`, `MIGRATION_PASSWORD`, and optional `PORT` (8081) separately for this process. No passwords have defaults.

```powershell
$env:MAVEN_USER_HOME = 'D:\agies\.m2'
.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B -pl services/checkout-service -am test
.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B -pl services/checkout-service -am package
java '-Duser.timezone=UTC' -jar services/checkout-service/target/checkout-service-0.1.0-SNAPSHOT.jar
```

Checkout's V1 creates only the purchase read model and primary-key index, with no seed records. Sixteen hypothetical test-only snapshots cover every valid state/step/flag/price combination, including long monetary and integer quantity boundaries. They do not prove payment success, stock commitment, or executed workflows. The default empty database therefore returns 404 for purchase queries. Full provisioning/Compose remains M1.4; no purchase creation API exists yet.

Database CHECK constraints protect static representations, including paired positive INR amounts, contract snapshot combinations and finite ordered timestamps. They cannot prove cross-service evidence or legal historical transitions. M1.3 must separately add durable intent, guarded transitions, stable operation identities and recovery obligations through new migrations.

E verification: **62 Checkout tests; 166 full-reactor tests, zero failures/errors/skips**. Checkout includes 8 unit, 8 MVC, 45 PostgreSQL/application/migration/isolation, and 1 real storage-loss case. All existing service regressions pass. The matrix test checks 400 candidates (16 valid, 384 rejected) within one test; these are not 400 independently counted tests. The standalone executable JAR started against disposable PostgreSQL with separate owner/reader roles and no purchase seed records. See D-014 in [DECISIONS.md](docs/DECISIONS.md) for commands, corrected initial failures, limitations and changed-file inventory. Logs: `logs/m1.2-e-*.log`; XML reports: `services/*/target/surefire-reports/`. E awaits acceptance; F/M1.3 have not begun.
