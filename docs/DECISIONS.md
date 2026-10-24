# Aegis architecture decisions

Current status (2026-10-24): M1.2 and refined M1.3-A accepted for progression. M1.3-B contract/persistence foundations implemented and verified, awaiting user acceptance. C/later unauthorized. D-017 is the current implementation record.

**Approved** means accepted baseline, not necessarily implemented capability. **Proposed** means pending user review; **Deferred** means intentionally unresolved. Current authorization permits M1.3-B contract/persistence foundations only. Leave changes uncommitted. Historical restrictions, branch descriptions and verification records below describe their dated checkpoints.

## D-001 — Research testbed and scope

**Status: Approved.** Four services: Checkout, Inventory, Payment Simulator, Notification. One SKU per purchase, fixed catalog pricing, one currency, synthetic payments, no user cancellation initially. Notifications are outside the critical purchase path.

The checkout system supplies an interpretable distributed workload. It is not an e-commerce product. A modular monolith would be simpler operationally but would not provide the same independent network failure boundaries. Additional services and commerce features are deferred until justified by research needs.

## D-002 — Durable orchestration and uncertainty

**Status: Approved.** Checkout owns persisted workflow progress and stable operation identities. Each service owns local transactions; no transaction spans a remote call. Timeouts retain explicit unresolved outcomes. Compensation and reconciliation persist until obligations are settled.

Successful payment after reservation expiry requires refund and reconciliation, without silent replacement reservation. Notifications use future durable delivery intent outside confirmation. Broker-based choreography and distributed transactions are deferred alternatives. The small persisted workflow requires more recovery reasoning but avoids unnecessary infrastructure.

## D-003 — Database topology

**Status: Approved.** One PostgreSQL server, separate databases and restricted roles per service, no cross-service access. This provides logical data ownership, not physical resource isolation. Shared server/host contention is an experimental limitation and possible common failure source.

**Deferred:** separate database instances for isolation experiments; server patch/image pins and exact provisioning privileges. Runtime roles must not inherit administrative access.

## D-004 — Workload classes and safety

**Status: Approved distinction; proposed concrete advisory endpoints.** Separate critical synchronous, degradable synchronous, and non-critical background traffic. Optional stock previews/summary enrichment are proposed degradable operations; they never authorize stock or payment decisions.

Recovery, compensation, and expiry remain correctness-critical even when executed in background workers. New unaccepted work can later be explicitly rejected; accepted mutations and unresolved effects must never be silently discarded. Admission control, shedding, concurrency limiting, and mitigation policies are deferred to Phase 3 onward. No policy may weaken business invariants.

## D-005 — Framework and managed testing baseline

**Status: Approved selection under user authorization to use a current stable Spring Boot release and managed testing dependencies.** Select Java 21 and Spring Boot **4.1.1** as the documentation version baseline. Use its managed testing stack rather than overriding it solely to keep JUnit 5. Official managed coordinates list JUnit Jupiter **6.0.3** and Testcontainers **2.0.5**.

Verified from official documentation on 2026-10-16:

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html) identifies 4.1.1 as stable, requires Java 17 or later through Java 26, and supports Maven 3.6.3 or later. Java 21 falls within that documented range.
- [Spring Boot managed dependency coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html) lists the managed JUnit and Testcontainers versions above.

This supersedes the initial suggestion to retain JUnit 5 or use an older Boot line merely for that purpose. This is a documented compatibility decision, not a tested build: no Maven configuration exists and no dependencies have been resolved. These official URLs are moving references; retain the checked versions/date here and reverify stable availability, support, and coordinates before pinning implementation versions in M1.2. A changed baseline must be recorded explicitly.

**Approved direction:** Java/Spring backend, Maven multi-module build, PostgreSQL, eventual Docker Compose. **Proposed implementation strategy:** Boot parent/BOM management, centralized plugin versions, compiler release 21, Maven wrapper, pinned images, and no unjustified dependency overrides. Exact Maven/JDK distribution patches and PostgreSQL server version are deferred. Framework transitive requirements should be managed by Boot rather than pinned independently without cause.

## D-006 — Persistence and contract details

**Status: Approved.** Spring MVC, Spring JDBC for visible SQL/concurrency semantics, Flyway for service-owned migrations, and PostgreSQL integration testing with managed Testcontainers. Synchronous REST clients remain future M1.3 adapters. Essential dependencies should cover web, validation, JDBC, driver, migrations, and testing only when their milestone is authorized.

JPA is an alternative with useful mapping convenience but additional ORM behavior to learn. Begin with wire specifications/examples rather than a shared Java DTO library. Never share persistence entities or service implementations. Inventory catalog/price authority, INR integer minor units, UUID resource identities, `/api/v1`, OpenAPI 3.x, RFC 9457-style errors and no shared Java implementation library are approved. Contracts now define the wire details; remaining M1.3 operational algorithms require later review. See [ARCHITECTURE.md](ARCHITECTURE.md).

## D-007 — Scope and authorization gates

**Status: Approved.** Preserve all six phases and 25 milestone names in [ROADMAP.md](ROADMAP.md). M1.2 covers scaffolding/contracts/local persistence/validation/service tests; M1.3 covers orchestration/idempotency/compensation/reconciliation/concurrency/recovery; M1.4 covers Compose/end-to-end/restart/reproducibility.

Historical A authorization permitted the four OpenAPI contracts and updates to six existing documentation artifacts only, with no implementation. The user subsequently authorized B then C; see D-012. Each later checkpoint still requires explicit authorization.

## D-008 — Infrastructure restraint and future separation

**Status: Approved constraints.** Local operation without required cloud services; prefer free open-source tools. AWS requires separate explicit approval and verified free usage eligibility. Do not introduce paid APIs, managed cloud databases, or commercial monitoring dependencies.

**Deferred:** OpenTelemetry/Prometheus/Grafana (Phase 2), resilience libraries (Phase 3), policy/safety/action modules (Phase 4), offline Python/modeling and possible ONNX Runtime Java (Phase 5), React dashboard (M6.1). Kafka, Redis, Kubernetes, service meshes, native images, and virtual threads have no demonstrated initial requirement. Telemetry, policies, and inference must remain separate from business correctness.

## D-009 — Verification and research integrity

**Status: Approved.** Enforce stock conservation, concurrent reservation correctness, durable idempotency, duplicate charge prevention, explicit uncertainty, single reservation terminal outcome, compensation, and notification isolation. Future tests use real PostgreSQL for transaction behavior, controlled race synchronization, response suppression, crash points, and process restart at the appropriate milestone.

Predictive benefit is a hypothesis. No results or novelty are claimed. Compare baselines with identical safety rules, workloads, resources, and reported tuning opportunities. Future experimental protocols must address class-specific outcomes, rejected work, overhead, leakage, held-out scenarios, and uncertainty.

## Open decisions by review point

| Review point | Unresolved questions |
|---|---|
| M1.2-A user review | Review prepared contracts and documentation; authorize M1.2-B separately. |
| Before M1.2 implementation | Verify actual build and dependency resolution; pin exact toolchain/database image versions; choose catalog fixture values. JDBC, Inventory authority, INR, versioned paths and error format are accepted. Advisory endpoint remains deferred. |
| Before M1.3 implementation | Client key scope/retention; reservation duration; exact fresh-database-time/locking linearization implementation; workflow deadlines; retry budgets; worker claims; terminal failure/fencing semantics; manual attention policy. |
| Before isolation experiments | Separate PostgreSQL instances, resource budgets, shared-host limitations. |
| Before research evaluation | Workload mix, failure distributions, tuning protocol, metrics/denominators, statistical analysis, and generalization limits. |

No unresolved issue blocks preparation of the authorized documentation. Future feature approval must resolve its applicable questions before coding.

## D-010 - M1.2-A wire contracts and notification replay

**Status: Accepted M1.2-A design; historical preparation details below.** Four independently packaged applications; OpenAPI 3.1.0 wire documents in JSON under `contracts/`, without shared Java implementations. JSON is permitted by [OpenAPI 3.1.0](https://spec.openapis.org/oas/v3.1.0.html). Errors follow [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html) with Aegis `code`/`violations` extensions. Each operation labels its future implementation milestone and current not-implemented status.

M1.2 endpoint scope: catalog GET, purchase GET, payment GET, notification POST/GET. Checkout/Payment reads use test fixtures, not fictional production outcomes. Purchase submission, reservations, charge/refund execution, and recovery remain M1.3. Optional research advisory endpoints remain deferred.

Notification replay is explicitly brought into M1.2: 201 for new ID; 200 and original receipt for identical semantic payload; 409 without mutation for conflict. Enforce database uniqueness and compare committed semantic fields under concurrency. This supersedes the earlier review proposal to return 409 for every duplicate until M1.3. No separate deduplication/recovery table is necessary.

**Minimal persistence approved:** catalog, purchase query representation, payment query representation, notification receipt only. Future reservation/refund/recovery/outbox structures are documented separately, not implemented or migrated in M1.2. Database/role provisioning for manual development remains a prerequisite; tests use Testcontainers. Complete reproducible deployment is M1.4.

The Java 21/Boot 4.1.1 documentation baseline is accepted subject to actual build verification. Prior suggested Maven/PostgreSQL patch pins are candidates, not an installed or build-tested lock. No new version verification or build is claimed by this contract-only checkpoint.

## Historical M1.2-A verification record (2026-10-17)

No dedicated OpenAPI validator or Python JSON Schema/OpenAPI validation package is installed in the inspected environment. No dependency was installed. Standard-library structural checks are used for JSON parsing, local references, required properties, parameter coverage, response codes/media types, milestone labels, and contract examples, plus semantic review of replay and failure rules. These checks are partial and do not establish full OpenAPI/JSON Schema conformance. Run a dedicated OpenAPI 3.1 validator when tooling is available under a separately authorized checkpoint; report any limitations honestly. No application tests or builds have run.

Preparation checks executed on 2026-10-17: all four JSON documents parsed; 83 local references resolved; required-property declarations, unique operation IDs, path-parameter coverage, response codes/media types and Location headers checked; 65 embedded examples passed the limited checker; six invalid notification payload variants were rejected. Five operations are M1.2 scope and eight are M1.3 reserved, all marked not implemented. Notification 201/200 examples preserve the same original receipt. This is contract consistency evidence only, not runtime or concurrency verification.

Repository checks found exactly the ten authorized documentation/contract files. All local Markdown links resolved, fences/encoding checks passed, and six phase headings plus 25 milestone lines matched the supplied original roadmap in order. A full OpenAPI validator remains unavailable; build/runtime checks await later authorization.


## D-011 - Focused contract state and error refinement

Status: M1.2-A clarifications accepted for subsequent B/C progression; the following records the earlier contract-only refinement. It did not itself authorize implementation; D-012 records the later explicit authorization.

The Checkout contract now constrains valid state/step/knowledge/compensation/price combinations with oneOf/allOf and supplies valid fixtures. Payment decline is definitive, never a synonym for transport/processing failure. Refund FAILED is a definitive immutable no-refund outcome, not a retryable/unknown attempt; compensation remains outstanding. Reservation eligibility is strictly before expiry at fresh database time after contention waits, with terminal replay rules and no renewal. Detailed execution mechanisms remain M1.3.

All four contracts now define operation-specific business error codes and response-specific allowed status/code combinations. Positive int64 amounts, checked multiplication, and 400 MONETARY_OVERFLOW before mutation are explicit. Cross-resource evidence, historical transitions, monetary product equality and ordering between timestamps remain runtime invariants beyond schema-only validation.

The preceding verification counts describe the original preparation pass, not this refinement. Updated validation evidence is recorded below after execution. At that refinement checkpoint A was awaiting review and B approval was not inferred. Later explicit B/C authorization is recorded in D-012.

### Refinement verification results (2026-10-17)

- Dedicated OpenAPI 3.1 validator: unavailable; name/version/result not applicable. Python `openapi_spec_validator`, `jsonschema`, and `yaml` are absent. Local Node resolution found no Redocly CLI, Swagger Parser/CLI, Ajv or format package; the global npm package inventory is empty. No package was installed or downloaded.
- Actual checker: ad hoc Python standard-library structural/schema-subset checks running on Python 3.14.4, not a certified or complete OpenAPI validator. All four documents passed JSON parsing, 83 local reference resolutions, required-property checks, path parameter coverage, operation identity/milestone checks, response media/status/code constraints, and schema-subset example checks.
- 113 schema/media examples passed, 75 invalid-property/UUID/money/decline variants were rejected, and all 400 enumerated Checkout state/step/flag/price snapshots agreed with the documented matrix, including unpriced reservation cleanup. The checker exercises the used oneOf/allOf/if/then/const subset but does not establish full OpenAPI/JSON Schema conformance.
- Notification 201/200 examples preserve the same receipt. Representative monetary product and overflow boundary calculations passed; Inventory examples satisfy product equality and expiry ordering. These checks do not execute Java arithmetic or PostgreSQL behavior.
- Five M1.2 operations and eight M1.3-reserved operations remain not implemented. Local documentation links/fences and the six-phase/25-milestone roadmap checks passed. The repository still contains only ten documentation/contract files; this refinement changed only the four contracts, ARCHITECTURE.md, and DECISIONS.md.
- Full OpenAPI validation remains an explicit tooling limitation. Runtime evidence, actual database expiry serialization, concurrent replay, historical transitions, cross-service outcome proof, and checked arithmetic implementation require later authorized tests. No application source, POM/wrapper, migration, application test, dependency installation, service or database startup was performed.


## D-012 - Authorized B foundation and C Inventory implementation

The user explicitly authorized starting M1.2-B and then moving to M1.2-C on 2026-10-18, superseding the documentation-only restriction for these checkpoints. A is accepted for progression. The initial B reactor passed all four independent random-port HTTP startup tests and produced four executable JARs before C began. This is verification evidence, not fabricated retrospective user acceptance. C is now implemented and verified, pending user review; no D work has started.

### Build and dependencies

Java 21.0.10, Boot 4.1.1 and Maven 3.9.15 were actually used. The official Maven Wrapper 3.3.4 only-script launchers pin 3.9.15. Its distribution SHA-512 was compared with Maven Central, then the computed SHA-256 was pinned in wrapper properties; wrapper -version succeeded. Java release is 21; executable JAR classes have major version 65. Four service POMs inherit dependency/plugin management and have no implementation dependencies on each other. Inventory alone adds JDBC, Flyway/PostgreSQL, and PostgreSQL/Testcontainers/MVC test support; there is no JPA/Hibernate/shared business library.

Official compatibility was rechecked against [Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html); wrapper generation/checksum behavior follows [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/). Flyway PostgreSQL integration follows [Boot database initialization guidance](https://docs.spring.io/spring-boot/how-to/data-initialization.html). Actual build resolution and tests now supplement the earlier documentation-only compatibility claim.

### Inventory behavior and storage

Only GET /api/v1/items/{sku} is implemented. SKU validation runs before parameterized JDBC lookup. The response has exactly sku, unitPriceMinor, currency, availableQuantity, observedAt. Missing/invalid SKU returns contract-compatible 404/400; storage-unavailable and unexpected errors are sanitized Problem Details with SERVICE_UNAVAILABLE/INTERNAL_ERROR. No reservation handlers exist. Contracts were preserved without changes; their implementation metadata is the historical review snapshot. README is the current capability inventory.

V1 creates one local catalog table: unique valid SKU, positive bigint price, INR currency, and nonnegative bigint available stock. Store available stock directly because no allocation/accounting behavior exists yet; introduce conservation/accounting and atomic conditional mutations in M1.3. A stock read and nonnegative constraint alone cannot prevent overselling. A separate opt-in local-profile repeatable migration inserts two deterministic synthetic catalog items without overwriting existing rows. Default schema migrations insert no fixture. Other service databases/schemas and completed-commerce records are untouched.

The controller handles HTTP representation, the application service validates input and resolves missing items, and the repository owns explicit parameterized SQL. Spring JDBC keeps SQL/transaction behavior visible; JPA's entity/context machinery is unnecessary here. Flyway versions schema changes and validates applied checksums; evolve with new migrations rather than editing applied ones. Database checks remain effective for every writer, not just this controller. M1.3 must add atomic allocation, terminal transition serialization, idempotency and race/recovery tests before stock mutations exist.

### Executed commands and results

Commands ran from D:\agies. Network/Docker access required execution outside the restricted sandbox. Necessary dependency resolution and Docker Desktop startup were performed under the authorized task; no paid service or Compose deployment was introduced.

1. `java -version`: PASS, Java 21.0.10. `docker version`: initially no engine; after starting installed Docker Desktop, PASS (Linux engine 29.3.1).
2. `mvn.cmd '-Dmaven.repo.local=D:/agies/.m2' -B verify`: initial foundation PASS, four startup tests and four JARs. Earlier unquoted PowerShell property invocation failed and was corrected; an initial sandbox download denial was resolved through approved network execution.
3. `mvn.cmd '-Dmaven.repo.local=D:/agies/.m2' -B org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper '-Dmaven=3.9.15' '-Dtype=only-script'`: PASS. SHA verification and wrapper `-version`: PASS.
4. `.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B verify`: first C run failed because PostgreSQL rejected JDBC's legacy host timezone Asia/Calcutta. Test JVMs now explicitly use UTC. Manual Inventory launch requires `java '-Duser.timezone=UTC' -jar ...` for portability with this image.
5. `.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B -pl services/inventory-service -am test`: rerun had one assertion failure (incorrectly expected six response fields; contract specifies five). Corrected to assert the exact five names; other database/constraint/role tests passed.
6. `.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B verify`: final PASS, all five reactor projects successful. 34 tests, zero failures/errors/skips: Inventory 31 (10 service, 6 MVC, 15 real-PostgreSQL/startup/API/migration/role cases), other applications 3 startup tests. Reports: `services/*/target/surefire-reports`; final log: `logs/final-build.log` (ignored build artifact).
7. Packaging inspection: PASS, four executable Boot JARs, correct Start-Class entries and Java 21 bytecode; Inventory migration packaged. Contract surface inspection: response component names match the contract, catalog mapping only, no inter-service implementation dependencies. Full OpenAPI validator remains unavailable; API assertions/static contract comparison are not full schema conformance certification.

PostgreSQL tests use 17.11 Testcontainers and separate owner/read-only runtime credentials. Flyway migrate/validate succeeds; invalid price/quantity/currency/SKU/null/duplicate rows are rejected. Runtime DDL and stock writes are denied; connection to a separate test service database is denied with SQLSTATE 42501. No H2 substitution or disabled-on-missing-Docker skip is used.

### Acceptance and limitations

B foundation and C endpoint/schema/fixture/tests/packaging are verified and ready for user review. Docker must be running for the database tests; first execution downloads dependencies/images. Manual local database/role provisioning remains a prerequisite, not a provided full deployment. UTC JVM configuration avoids the observed legacy timezone alias failure. Mockito emits a non-failing future-JDK dynamic-agent warning. No performance measurements or research conclusions are asserted.

Reserved for M1.3: reservation create/commit/release/expiry, stock allocation, workflow/payment idempotency, compensation, reconciliation, orchestration and service calls. No adaptive policy, fault injection, monitoring stack, AWS, or M1.2-D functionality was implemented.

### Source file inventory for B/C

Created root/build and service files (generated caches, logs and target artifacts excluded):

```text
pom.xml
.gitignore
mvnw
mvnw.cmd
.mvn/wrapper/maven-wrapper.properties
services/checkout-service/pom.xml
services/checkout-service/src/main/java/io/aegis/checkout/CheckoutApplication.java
services/checkout-service/src/main/resources/application.properties
services/checkout-service/src/test/java/io/aegis/checkout/CheckoutApplicationTest.java
services/inventory-service/pom.xml
services/inventory-service/src/main/java/io/aegis/inventory/api/CatalogController.java
services/inventory-service/src/main/java/io/aegis/inventory/api/CatalogErrors.java
services/inventory-service/src/main/java/io/aegis/inventory/api/CatalogResponse.java
services/inventory-service/src/main/java/io/aegis/inventory/application/CatalogService.java
services/inventory-service/src/main/java/io/aegis/inventory/application/InvalidSkuException.java
services/inventory-service/src/main/java/io/aegis/inventory/application/ItemNotFoundException.java
services/inventory-service/src/main/java/io/aegis/inventory/config/TimeConfiguration.java
services/inventory-service/src/main/java/io/aegis/inventory/domain/CatalogItem.java
services/inventory-service/src/main/java/io/aegis/inventory/infrastructure/persistence/CatalogRepository.java
services/inventory-service/src/main/java/io/aegis/inventory/InventoryApplication.java
services/inventory-service/src/main/resources/application-local.properties
services/inventory-service/src/main/resources/application.properties
services/inventory-service/src/main/resources/db/local/R__synthetic_catalog.sql
services/inventory-service/src/main/resources/db/migration/V1__create_catalog_item.sql
services/inventory-service/src/test/java/io/aegis/inventory/api/CatalogControllerTest.java
services/inventory-service/src/test/java/io/aegis/inventory/application/CatalogServiceTest.java
services/inventory-service/src/test/java/io/aegis/inventory/InventoryApplicationTest.java
services/notification-service/pom.xml
services/notification-service/src/main/java/io/aegis/notification/NotificationApplication.java
services/notification-service/src/main/resources/application.properties
services/notification-service/src/test/java/io/aegis/notification/NotificationApplicationTest.java
services/payment-simulator/pom.xml
services/payment-simulator/src/main/java/io/aegis/payment/PaymentApplication.java
services/payment-simulator/src/main/resources/application.properties
services/payment-simulator/src/test/java/io/aegis/payment/PaymentApplicationTest.java
```

Modified existing documentation: README.md, AGENTS.md, docs/PROJECT_SPEC.md, docs/ARCHITECTURE.md, docs/ROADMAP.md, docs/DECISIONS.md. No contract files were changed.


## D-013 - M1.2-D Payment queries and durable Notification receipts

**Status: accepted for progression by subsequent E authorization.** The following is the historical D implementation report. The user explicitly accepted C and authorized D only at that time. Initial working tree on main was clean; the existing full reactor baseline passed 34 tests before implementation. Created feature/m1.2-d-payment-notification. No source changes to Inventory/Checkout, parent POM, wrapper or contracts; no merge, commit, force-push or push was performed. The previous commit/push authorization concerned the prior main snapshot, not new D work.

### Accepted scope and schemas

Payment implements GET /api/v1/payments/{paymentId} only, using strict UUID lexical validation and service-local JDBC. Required null declineCode remains present. PENDING means no definitive outcome; DECLINED is definitive with SIMULATED_DECLINE. Missing record and unavailable storage never become a decline or invented success. V1 creates payment with UUID PK, unique purchase UUID, reservation UUID value, positive bigint amount, INR, three valid states, explicit decline consistency, and ordered timestamps. No outcome rows are inserted by migrations or application startup; test records live in src/test/resources/fixtures/payments.sql and are reset before each integration case. No refund schema or charge executor exists.

Notification implements POST /api/v1/notifications and GET /api/v1/notifications/{notificationId}. V1 creates notification_receipt with UUID PK, purchase UUID, fixed kind/state and database-generated creation timestamp. STORED means only synthetic local receipt. Input is strict JSON with three required string fields, UUID validation and enum validation. No outbox, delivery worker or messaging API exists.

Both modules follow constructor-injected controller/application/domain/JDBC boundaries. They inherit existing managed dependencies and add only Inventory-established JDBC/Flyway/PostgreSQL/Testcontainers/MVC test artifacts. No shared implementation module, JPA, cloud or new infrastructure framework is introduced. Defaults identify distinct service databases; DB_URL/DB_USER/DB_PASSWORD and MIGRATION_USER/MIGRATION_PASSWORD are environment-driven per process. Read-only Payment runtime role and append/read-only Notification runtime role remain separate from migration owner credentials. Notification INSERT grants should cover request columns only; UPDATE/DELETE/DDL and cross-database connection privileges are denied.

### Concurrency decision and learning notes

A primary key, rather than JVM locks or an in-memory map, coordinates identities across application instances and restarts. Notification uses a short Spring READ COMMITTED transaction. INSERT ON CONFLICT DO NOTHING RETURNING either supplies a new row or waits for a competing identity to resolve. A separate SELECT then gets a fresh committed snapshot to compare the winning purchase UUID/kind. This avoids the SELECT-before-INSERT race and the single-statement insert/select snapshot gap. No raw unique exception is caught and followed by queries in an aborted transaction. Mismatches roll back without updating the accepted receipt. Spring's transaction proxy commits before the controller emits 201/200. Commit/storage failure can have an unknown outcome and is not reported as success. Retrying the same identity resolves ambiguity; a timeout does not prove rollback.

Normal PostgreSQL durability settings are assumed; these tests verify commit visibility and application restart retention, not power-loss resilience. Runtime permissions and no deletion route make receipts immutable to the protocol; privileged administrative changes are outside it. Official semantics: [PostgreSQL READ COMMITTED](https://www.postgresql.org/docs/17/transaction-iso.html), [INSERT/ON CONFLICT](https://www.postgresql.org/docs/17/sql-insert.html).

Payment stays read-only because executing charges before M1.3 would introduce workflow/idempotency/uncertain-side-effect behavior prematurely. Its migration validates stored read models only. Flyway scripts and credentials remain service-owned, with no cross-service foreign keys or SQL. Testcontainers exercises actual PostgreSQL uniqueness, isolation, rollback and role checks; mocks are used only for unit/API error translation, never concurrency acceptance.

### Executed verification (2026-10-18)

All commands ran from D:\agies with MAVEN_USER_HOME=D:\agies\.m2. Logs remain under ignored logs/. The sandbox process helper initially failed to initialize; execution outside it allowed repository review/builds and Docker access. No application/test blocker was found.

| Command | Actual result |
|---|---|
| `.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B verify` before changes | PASS, 34 baseline tests; logs/m1.2-d-baseline.log |
| `.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B -pl services/payment-simulator,services/notification-service -am '-DskipTests' compile` | PASS, compilation only; not a test run; logs/m1.2-d-compile.log |
| `.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B -pl services/payment-simulator,services/notification-service -am test` | PASS, initial 71 service tests before two additional outage tests; logs/m1.2-d-services.log |
| `.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B verify` after all source/test changes | PASS, 105 tests, zero failures/errors/skips; logs/m1.2-d-reactor.log |

Final report counts from Surefire XML:

| Service | Tests | Coverage |
|---|---:|---|
| Checkout | 1 | Unchanged independent HTTP startup regression |
| Inventory | 31 | Unchanged unit/MVC/PostgreSQL/Flyway/role regressions |
| Payment | 34 | 8 unit, 6 MVC, 19 PostgreSQL/application, 1 real outage |
| Notification | 39 | 8 unit, 16 MVC, 14 PostgreSQL/multi-instance/restart, 1 real outage |

Payment tests cover exact response fields read from the contract, required nulls, all stored states, amount/currency including Long.MAX_VALUE, unknown/invalid IDs, constraints (including NULL decline rejection), identity uniqueness, migrations and restricted roles. Separate isolated database-loss test verifies real 503 behavior. Test data is disposable and reset for independent repeatability.

Notification uses two separately started Spring application contexts, HTTP servers and pools sharing real PostgreSQL. Eight-client start gates verify one 201 plus seven 200 identical replays, or one 201 plus seven 409 conflicting replays. A held uncommitted insertion and observed PostgreSQL lock wait verify both commit (200 replay) and rollback (201 new) outcomes without guessed sleeps. Separate connections verify one committed winner before success consumption; matching replays retain the exact original receipt/createdAt after conflict. Closing/recreating one application context preserves receipt and replay behavior. Restricted roles cannot mutate/delete or connect to a sibling database. Isolated database loss produces 503 for GET and POST; a mocked commit error also cannot yield 201/200.

Final packaging inspection passed for all four executable Boot JARs: correct Start-Class and Java 21 bytecode (major 65). Each new service includes exactly one V1 migration; payment fixtures are not packaged in production. Source route checks and MVC tests verify no charge/refund/reserved mutation handlers. Response-field tests read authoritative OpenAPI required fields; statuses, nullable decline values, enums, errors and JSON validation are asserted. Full OpenAPI validator remains unavailable, so this is not a certification of full schema conformance. All contracts remain byte-for-byte unchanged in Git.

Documentation links/fences, exact six-phase/25-milestone roadmap, scope and whitespace checks passed. Existing Inventory/Checkout source and parent build configuration are unchanged. Working tree intentionally has uncommitted D code/docs on the feature branch, ready for review. No tests failed or were skipped in the executed D runs.

### Limitations and acceptance gate

No blocking issue remains. Docker and first-run image/dependency access are required. Existing non-failing Mockito dynamic-agent warnings and Jackson convenience-method deprecation warnings remain visible; versions are not overridden to hide them. Manual database/role provisioning is still required outside tests, with UTC JVM launch for the known host timezone alias issue. Tests use independent application contexts in one JVM; they do not claim independent OS-process crash or deployment recovery evidence. Full deployment/reproducibility remains M1.4.

D is ready for user acceptance review, not accepted automatically. E Checkout queries, M1.3 charge/refund execution, workflows/reconciliation, retries, actual notification delivery, and all later mechanisms remain deferred and unauthorized.

### D changed-file inventory

```text
AGENTS.md
README.md
docs/ARCHITECTURE.md
docs/DECISIONS.md
docs/PROJECT_SPEC.md
docs/ROADMAP.md
services/notification-service/pom.xml
services/notification-service/src/main/java/io/aegis/notification/api/ApiErrors.java
services/notification-service/src/main/java/io/aegis/notification/api/NotificationController.java
services/notification-service/src/main/java/io/aegis/notification/api/NotificationRequest.java
services/notification-service/src/main/java/io/aegis/notification/application/InvalidRequestException.java
services/notification-service/src/main/java/io/aegis/notification/application/NotificationConflictException.java
services/notification-service/src/main/java/io/aegis/notification/application/NotificationNotFoundException.java
services/notification-service/src/main/java/io/aegis/notification/application/NotificationService.java
services/notification-service/src/main/java/io/aegis/notification/application/RequestIds.java
services/notification-service/src/main/java/io/aegis/notification/domain/NotificationReceipt.java
services/notification-service/src/main/java/io/aegis/notification/infrastructure/persistence/NotificationRepository.java
services/notification-service/src/main/resources/application.properties
services/notification-service/src/main/resources/db/migration/V1__create_notification_receipt.sql
services/notification-service/src/test/java/io/aegis/notification/NotificationApplicationTest.java
services/notification-service/src/test/java/io/aegis/notification/StorageUnavailableTest.java
services/notification-service/src/test/java/io/aegis/notification/api/NotificationControllerTest.java
services/notification-service/src/test/java/io/aegis/notification/application/NotificationServiceTest.java
services/payment-simulator/pom.xml
services/payment-simulator/src/main/java/io/aegis/payment/api/ApiErrors.java
services/payment-simulator/src/main/java/io/aegis/payment/api/PaymentController.java
services/payment-simulator/src/main/java/io/aegis/payment/application/InvalidRequestException.java
services/payment-simulator/src/main/java/io/aegis/payment/application/PaymentNotFoundException.java
services/payment-simulator/src/main/java/io/aegis/payment/application/PaymentService.java
services/payment-simulator/src/main/java/io/aegis/payment/application/RequestIds.java
services/payment-simulator/src/main/java/io/aegis/payment/domain/Payment.java
services/payment-simulator/src/main/java/io/aegis/payment/infrastructure/persistence/PaymentRepository.java
services/payment-simulator/src/main/resources/application.properties
services/payment-simulator/src/main/resources/db/migration/V1__create_payment.sql
services/payment-simulator/src/test/java/io/aegis/payment/PaymentApplicationTest.java
services/payment-simulator/src/test/java/io/aegis/payment/StorageUnavailableTest.java
services/payment-simulator/src/test/java/io/aegis/payment/api/PaymentControllerTest.java
services/payment-simulator/src/test/java/io/aegis/payment/application/PaymentServiceTest.java
services/payment-simulator/src/test/resources/fixtures/payments.sql
```

## D-014 - M1.2-E Checkout purchase query implementation

**Status: implemented and verified; awaiting explicit user acceptance.** Previous checkpoints through D were accepted for progression by the user's E authorization. Clean local main at 1435181 contained the accepted D implementation. Created feature/m1.2-e-checkout from that local main only after successful baseline verification. E changes remain uncommitted; no merge, commit, push or history rewrite was performed. F, M1.3 and later are not authorized.

### Design and learning decisions

Checkout now implements GET /api/v1/purchases/{purchaseId} only. Controller -> PurchaseService -> PurchaseRepository -> Checkout PostgreSQL is the complete request path. Strict lexical UUID validation accepts canonical case variants and rejects shortened UUID syntax before SQL. The service returns stored state; missing data returns PURCHASE_NOT_FOUND, never an inferred failed workflow. RFC 9457-style errors distinguish 400 INVALID_REQUEST, 404 PURCHASE_NOT_FOUND, 503 SERVICE_UNAVAILABLE (resource failure/query timeout), and sanitized 500 INTERNAL_ERROR (including SQL programming errors). All eleven properties are returned, with explicit nullable Long amountMinor and nullable currency.

Checkout owns purchase snapshots because future orchestration needs one durable authority for progress. Inventory remains price authority. Amount/currency stay absent together before reservation establishes a price; zero is not an unknown sentinel. Spring JDBC exposes the single parameterized SELECT directly without JPA mapping/context machinery. No transaction or remote call is necessary for this one-statement read. There are no mutation methods, HTTP clients, workers, compensation handlers or submission routes.

V1 creates one purchase table with UUID primary key (the only index), SKU format/length, positive int32 quantity, paired null or positive bigint/INR price, non-null state/step/knowledge/compensation flags, and finite ordered timestamptz values. The ten OpenAPI state/step branches allow sixteen complete flag/price shapes. Explicit IS NULL/IS NOT NULL conditions prevent CHECK/UNKNOWN from admitting partially missing prices. Known payment requires price; CONFIRMED requires known payment and no pending compensation; FAILED requires SETTLED and no pending compensation. The full contract matrix constrains the other combinations.

These CHECK constraints protect static representations from every writer. They cannot prove actual charge success, inventory commitment, settled remote effects, or legal historical transitions. Test-only snapshots are hypothetical, not evidence of executed workflows. Those facts must be established by separately authorized M1.3 algorithms before such state can be written by the application. Future migrations can add durable operation identities, obligation records, transition guards and recovery metadata while retaining purchase identity. E adds no speculative fields/tables.

Flyway owns only Checkout's versioned migration/history. No production seeds exist. Separate environment-driven migration owner and runtime credentials follow existing service configuration. Runtime needs CONNECT, schema USAGE and SELECT on purchase only; no mutation, DDL, migration-history read or cross-database access. Tests provision exact grants externally; the packaged-JAR smoke uses a non-superuser migration role owning its disposable database and a separate reader. Manual development provisioning remains required; Compose belongs to M1.4. No dependency versions, shared libraries or infrastructure were changed; Checkout adds only the JDBC/Flyway/PostgreSQL and MVC/Testcontainers test artifacts already established in the other services.

### Executed verification (2026-10-20 through 2026-10-21)

PowerShell commands ran from D:\agies with MAVEN_USER_HOME=D:\agies\.m2. Logs are ignored local artifacts. Docker Desktop 4.67.0 / Linux Engine 29.3.1 and real PostgreSQL 17.11 were available after prerequisite recovery.

| Exact command | Actual result |
|---|---|
| `docker version` | PASS; Client and Linux Server available |
| `.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B verify` before E changes | PASS: 105 tests, 0 failures/errors/skips; logs/m1.2-e-baseline-retry.log |
| `.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B -pl services/checkout-service -am test` first run | FAIL: 60 tests, 2 failures, 0 errors/skips. Tests exposed copied paymentId route parameter naming and incorrect matrix cardinality expectation; corrected both |
| Same targeted command after corrections | PASS: 62 tests, 0 failures/errors/skips; logs/m1.2-e-checkout.log |
| `.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B verify` after final code/fixture changes | PASS: 166 tests, 0 failures/errors/skips; logs/m1.2-e-reactor.log |
| `.\mvnw.cmd '-Dmaven.repo.local=D:/agies/.m2' -B -pl services/checkout-service -am '-DskipTests' package` | PASS: independent executable JAR packaging; tests intentionally not rerun by this packaging command; logs/m1.2-e-package.log |
| `java -Duser.timezone=UTC -jar D:\agies\services\checkout-service\target\checkout-service-0.1.0-SNAPSHOT.jar --server.port=0 --logging.level.root=INFO --spring.main.banner-mode=off` | PASS: actual packaged process with environment-driven reader/migrator credentials and disposable local PostgreSQL; GET unknown returned 404 PURCHASE_NOT_FOUND; purchase count 0, Flyway V1 success; logs/m1.2-e-jar-stdout.log and stderr.log |

The original prerequisite attempt failed because Docker was unavailable; no application change preceded that stop. The first optional JAR smoke's provisioning raced the image's temporary Unix-socket initialization server. Corrected its readiness probe to `docker exec <verification-container> pg_isready -h 127.0.0.1 -U postgres`, then the complete smoke passed. Both disposable smoke containers and the one launched Java process were cleaned up; process environment values were restored. No persistent deployment or Compose files were created.

An automatic permission review timed out while retrieving results; a permitted read-only retry succeeded. The full reactor log reports an unusually long 32:56 elapsed duration including an Inventory stage of 32:03 during that wall-clock interruption. All tests passed; this run is verification evidence, not a performance measurement, and no cause beyond observed environmental delay is asserted.

| Service / group | Tests | Failures / errors / skips |
|---|---:|---|
| Checkout application-service unit | 8 | 0 / 0 / 0 |
| Checkout MVC/API | 8 | 0 / 0 / 0 |
| Checkout PostgreSQL/application/migrations/constraints/roles | 45 | 0 / 0 / 0 |
| Checkout real database-loss | 1 | 0 / 0 / 0 |
| Inventory unchanged regression | 31 | 0 / 0 / 0 |
| Payment unchanged regression | 34 | 0 / 0 / 0 |
| Notification unchanged regression | 39 | 0 / 0 / 0 |
| **Full reactor** | **166** | **0 / 0 / 0** |

The PostgreSQL matrix is one test containing 400 checks, not 400 separately counted tests: 16 valid combinations inserted and 384 invalid combinations rejected. It independently varies state, step, known flag, compensation flag, amount presence and currency presence against the actual contract's oneOf/allOf/if/then constraints. Every valid representation is exercised through HTTP with exact full-body/required-null/state/flag/timestamp equality and a before/after owner-side comparison proving GET left all stored fields unchanged. Fixtures include Long.MAX_VALUE (quantity 1), int32 quantity maximum before pricing, and a 64-character SKU. Invalid constraints include nulls, partial/negative/zero prices, quantity, SKU, invalid states, unordered/non-finite timestamps, uniqueness and range overflow. Separate tests cover UTC offset normalization, missing/invalid/case-varied identities, denied writes/DDL/history access/cross-database connections, and real storage disappearance returning sanitized 503. Mocked failures cover unexpected errors and query timeout classification only.

Flyway migrate/validate, zero pending/one applied migration, an initially empty database, and no reseeding on repeat migrate passed. Actual Spring handler inspection finds exactly the purchase GET business route; API tests reject purchase POST (404 collection / 405 existing item route). Source inspection finds no remote clients, schedulers, transitions or transaction handlers. JAR inspection confirms correct Start-Class, Java 21 major version 65, exactly V1__create_purchase.sql and no test fixtures.

Dedicated OpenAPI 3.1 validation was not executed: Python openapi_spec_validator and jsonschema are not installed. The test-only PurchaseContract helper checks the keywords used by the Purchase schema and response Problem shape, while explicit tests check statuses and error codes; it is not a general OpenAPI validator. Cross-service evidence and historical state semantics are not certifiable through JSON Schema. All approved contract files remain byte-for-byte unchanged.

Final documentation audit checks exact six phase headings and 25 milestone lines against main, local Markdown links/fences, authorization/status terminology and whitespace. Contracts, parent/wrapper, Inventory, Payment and Notification source/configuration remain unchanged. Logs/caches/target outputs remain ignored. E is ready for user acceptance; no unresolved application blocker remains. Existing Mockito dynamic-agent and Jackson convenience-method deprecation warnings are non-failing; no versions were changed to hide them. Manual provisioning, UTC JVM portability, full-validator tooling and absence of actual workflows remain explicit limitations.

### E changed-file inventory

```text
AGENTS.md
README.md
docs/ARCHITECTURE.md
docs/DECISIONS.md
docs/PROJECT_SPEC.md
docs/ROADMAP.md
services/checkout-service/pom.xml
services/checkout-service/src/main/java/io/aegis/checkout/api/ApiErrors.java
services/checkout-service/src/main/java/io/aegis/checkout/api/PurchaseController.java
services/checkout-service/src/main/java/io/aegis/checkout/application/InvalidRequestException.java
services/checkout-service/src/main/java/io/aegis/checkout/application/PurchaseNotFoundException.java
services/checkout-service/src/main/java/io/aegis/checkout/application/PurchaseService.java
services/checkout-service/src/main/java/io/aegis/checkout/application/RequestIds.java
services/checkout-service/src/main/java/io/aegis/checkout/domain/Purchase.java
services/checkout-service/src/main/java/io/aegis/checkout/infrastructure/persistence/PurchaseRepository.java
services/checkout-service/src/main/resources/application.properties
services/checkout-service/src/main/resources/db/migration/V1__create_purchase.sql
services/checkout-service/src/test/java/io/aegis/checkout/CheckoutApplicationTest.java
services/checkout-service/src/test/java/io/aegis/checkout/PurchaseContract.java
services/checkout-service/src/test/java/io/aegis/checkout/StorageUnavailableTest.java
services/checkout-service/src/test/java/io/aegis/checkout/api/PurchaseControllerTest.java
services/checkout-service/src/test/java/io/aegis/checkout/application/PurchaseServiceTest.java
services/checkout-service/src/test/resources/fixtures/purchases.json
```

## D-015 - M1.2 audit and acceptance for progression

**Status: accepted for progression by explicit user statement on 2026-10-24.** E was integrated through PR #2 as main 7fe74c1. The 2026-10-21 F audit reran the reactor: 166 tests, zero failures/errors/skips (Checkout 62, Inventory 31, Payment 34, Notification 39), plus four independent JAR starts using separate databases and restricted roles. The initial F packaging failure was an agent-owned stale E smoke process locking the JAR; verified cleanup and reactor retry passed. This corrects D-014's incomplete process-cleanup claim. No fresh build/test is claimed for today's documentation change.

Administrative status is now synchronized; implementation remains the five M1.2 endpoints. Payment V1 still permits non-finite timestamps: new migration/tests before writers remain open. Dedicated OpenAPI validation remains unavailable; partial checks are not certification. Acceptance for progression does not mean these follow-ups were fixed or that Phase 1/M1.3 is complete.

## D-016 - M1.3-A Saga correctness proposal

**Status: refined design accepted for B progression on 2026-10-24; CD-2 deferred.** Historical proposal preparation follows; D-017 records the subsequent authorization/implementation. Detailed design: [M1_3_TRANSACTION_DESIGN.md](M1_3_TRANSACTION_DESIGN.md). Approved M1.1/M1.2 constraints remain intact; A creates no implementation.

- P-1: durable Checkout acceptance -> reserve -> charge -> commit -> atomic confirmation/notification intent; notification outside the critical path; no opposing release/refund while a published commit may win.
- P-2: fixed persisted UUIDs, scoped semantic key replay, retained identities/negative results without automatic deletion.
- P-3: Inventory request-outcome ledger including definitive rejection; locked lifecycle and fresh DB time after all contention waits; conserved stock counters.
- P-4: once charge intent exists, reconcile same ID even after expiry; absent charge may require original PUT and then refund. No decline inferred from timeout/404. This cost needs explicit approval or a separately designed remote cancellation fence.
- P-5: durable intents/evidence, short local transactions, revision/generation claims and retained compensation obligations. Fencing protects local writes; downstream idempotency/terminal guards handle already-sent HTTP.

Contract deltas CD-1 through CD-7 are identified for review (negative reservation replay, refund reason, pending processing, timestamps, metadata, key scope and expiry-cleanup acknowledgement). No approved JSON changed. Existing purchase states and snapshot matrix are preserved. Budgets, checkpoint sequence and attention/remediation policy are proposed, not accepted defaults. Detailed tables include formal invariants/test falsifiers and all eighteen requested failure scenarios.

Documentation choice: one new detailed design file, with links/status corrections in the six existing documents, avoids copying algorithms across files. M1.3-B and later require separate explicit acceptance/authorization. No Java, migration, route, worker, dependency, infrastructure or application test changes are permitted in A.

### M1.3-A documentation verification (2026-10-24)

Executed checks: all seven changed Markdown documents have resolving local links and balanced code fences; exact six phase headings and 25 milestone lines match HEAD; only README, AGENTS, four baseline docs and the new design document changed. All four unchanged JSON contracts parsed and 83 local references resolved. Design coverage checks found eighteen failure cases, eighteen invariant/test entries and seven contract-delta proposals. git diff --check passed. Formal payment invariants use PAY-* to distinguish them from P-* proposal IDs.

Dedicated validation remains unavailable: Python openapi_spec_validator/jsonschema and local Node Redocly CLI/Swagger Parser/Ajv were not installed. No validator name/version/result is applicable; no dependency was installed and structural checks are not full OpenAPI conformance. No Maven build, application test, Java/SQL implementation, migration, infrastructure, deployment, commit or push occurred in this design-only checkpoint. The earlier 166-test result remains dated 2026-10-21. User review of the design and separate next-checkpoint authorization remain pending.

## D-017 - Accepted M1.3-A and authorized M1.3-B foundations

**Status: A accepted for progression; B implemented and verified, awaiting user acceptance.** The user confirmed proceeding with A then B after being asked whether refined P-1..P-5 and CD-1/CD-3..CD-7 were accepted with CD-2 deferred. This authorizes B only, not C or full Saga execution. Starting baseline main was 7fe74c1; seven existing A documentation changes were preserved and updated consistently. Work remains uncommitted on main, with no branch switch, merge, commit, push or history rewrite.

### Implemented schemas and storage behavior

| Service | New migration | Actual contents |
|---|---|---|
| Checkout | V2__transaction_foundations.sql | Canonical purchase_key binding; purchase_workflow IDs/revision/generation/lease/pause/scheduling and independent obligations; immutable workflow_intent; guarded workflow_observation; stable notification_intent requiring a confirmed snapshot. Existing Purchase wire shape unchanged. |
| Inventory | V2__transaction_foundations.sql | Immutable reservation_request with CREATED/REJECTED and canonical payload/retained error; reservation snapshots with local catalog/request FKs, positive checked product, price/currency/deadline and terminal guards; partial expiry index; deferred request/reservation consistency. No allocation/counter mutation/expiry executor. |
| Payment | V2__transaction_foundations.sql | Finite supported-range time checks on payment; immutable charge_policy; one full refund identity per successful charge with retained policy, reason, time/terminal guards; no executor. |

All four applied V1 migration contents are unchanged. Windows checkout CRLF differs from Git LF; a raw-byte HEAD comparison initially flagged that normalization, not an edit. Git diff and normalized-content verification confirm no V1 changes. Default databases gain no business outcome seeds. Only test fixtures represent hypothetical pending/terminal records. Existing test cleanup now uses disposable TRUNCATE ... CASCADE for service-local FKs; migration-count assertions reflect V2.

Checkout WorkflowRepository.initialize atomically arbitrates the scoped client key, compares canonical SKU/quantity after a uniqueness conflict, stores the initial unpriced PROCESSING snapshot, fixed IDs and reserve intent. It does not register POST or dispatch anything. A matching replay returns the existing workflow/IDs; a conflicting semantic payload rolls back. Claim locks the workflow, reads fresh DB time, rejects paused/terminal/live claims and increments revision/generation; observe requires owner/generation/revision/live lease and rejects terminal purchases. Observation CHECK/trigger guards prevent regressive/contradictory terminal values. No public Purchase advancement, retry worker, resumption mechanism or downstream evidence validation exists.

Inventory ReservationRepository retains a caller-decided rejection transactionally and returns the committed identity winner without recalculating error based on current stock. Changed payload/purchase conflicts; same-purchase/different-ID uniqueness is enforced. Read mapping covers complete Reservation fields. Actual request decision, stock movement, conservation counters, expiry time after lifecycle waits and mutation handlers remain C. Test snapshots with ACTIVE rows are hypothetical representation fixtures, not evidence of stock allocation.

PaymentFoundationRepository atomically stores a PENDING admission plus retained policy; replay preserves chosen inputs and rejects changed charge payload. Existing legacy PENDING without policy cannot acquire invented inputs. Refund admission stores PENDING only, requires a matching SUCCEEDED parent, enforces full amount/reason/identity uniqueness and retains policy. SyntheticPolicy.normal describes AEGIS_SUCCESS_V1/NORMAL_SUCCESS/SUCCEEDED but executes no effect. A later D executor must resolve admission within its enclosing local transaction for normal immediate terminal behavior. Legacy pending GET remains compatible. No charge/refund state executor or public mutation API is implemented.

These Spring JDBC repositories use constructor injection, parameterized SQL and short Spring-proxied local transactions. No remote HTTP or JVM lock participates. Storage exceptions are not yet new HTTP business-code mappings; future C/D/E application layers must implement those contracts. Canonical fields, not collision-prone hash equality alone, define replay. Separate test runtime writer credentials have SELECT/INSERT/UPDATE on owned business tables only, no DELETE/DDL/history access/cross-database connection; production read roles need no expanded grants for current GETs. Grant only required local actions when writer primitives are deliberately used. No service depends on another implementation module; no Maven dependency/version changed.

Correctness review removed an overly restrictive draft CHECK coupling refund_required to cleanup_required: refund may remain outstanding after Inventory cleanup settles. Tests now cover independence. Review also added terminal claim/observation exclusion and immutable workflow-bound purchase/notification identity guards before final verification.

### Contract changes and dedicated validation

Accepted CD-1/3/4/5/6/7 clarifications are in all four JSON contracts: retained negative reservation replay; current-resource pending resolution semantics with fixed policy inputs; finite supported-range timestamps; accurate implemented M1.2 metadata; key scope/retention; and committed expiry cleanup before conflict acknowledgement. Five M1.2 operation labels are implemented; eight M1.3 labels stay not-implemented. No reserved controller was registered. CD-2 remains deferred: RefundRequest still accepts only RESERVATION_EXPIRED. No public request/response properties, enums or existing M1.2 statuses were removed or changed.

Free OpenAPI validation tooling is isolated in ignored .tools/openapi-validator, not an application dependency or global install. An initial 0.7.2 install was upgraded before validation to openapi-spec-validator 0.9.0, whose [official package metadata](https://pypi.org/project/openapi-spec-validator/) supports Python 3.14 and OpenAPI3.1. Validator classes and jsonschema 4.26.0 ran successfully on all four specifications plus 25 component-schema examples. This checks the specifications/examples, not conformance or safety of unimplemented HTTP operations. It closes the missing-tool follow-up for specification validation, not the future runtime-contract gate.

### Verification evidence

All commands ran in D:\agies with MAVEN_USER_HOME=D:\agies\.m2 and MAVEN_OPTS=-Dmaven.repo.local=D:/agies/.m2; caches/logs/tools/target outputs are ignored. Installed Docker Desktop was initially stopped; launched hidden and waited for Linux Engine 29.3.1 before baseline. No cloud/Compose deployment. Existing Java21.0.10/Boot4.1.1/Maven3.9.15/PostgreSQL17.11 baseline unchanged.

| Command/run | Actual outcome |
|---|---|
| `.\mvnw.cmd -B verify` before changes | PASS166, zero failures/errors/skips; logs/m1.3-b-baseline.log |
| `.\mvnw.cmd -B '-DskipTests' package` | PASS all four JARs; test compilation only, execution intentionally skipped; logs/m1.3-b-compile.log |
| `.\mvnw.cmd -B verify` initial foundation run | PASS221, zero failures/errors/skips; logs/m1.3-b-first.log |
| Same command after schema/contention hardening | PASS225, zero failures/errors/skips; logs/m1.3-b-final.log |
| Same command after new upgrade tests | FAIL: Checkout83, one assertion failure, zero errors/skips; later modules not run. Upgrade assertion counted Flyway's schema-creation marker as a third migration. Corrected to count SQL migrations, no schema repair needed; logs/m1.3-b-verified.log |
| Same command after count correction | PASS229, zero failures/errors/skips; logs/m1.3-b-accepted-verification.log |
| Final `.\mvnw.cmd -B verify` after independent-obligation/terminal-eligibility correction | PASS231, zero failures/errors/skips; logs/m1.3-b-release-check.log |
| `.\.tools\openapi-validator\Scripts\python.exe` using OpenAPIV31SpecValidator and Draft202012Validator/FormatChecker | PASS4 specifications and25 examples; version0.9.0/jsonschema4.26.0; logs/m1.3-b-contract-validation.log |

Tests added: PostgreSQL mappings, six-client matching/conflicting key races, retained rejection races, charge admission/policy replay and refund parent-uniqueness contention, transaction rollback, immutable identity/payload/policy, monotone observations, lease generation stale rejection, independent obligations, terminal eligibility, role isolation and migration upgrades preserving V1 data. Payment invalid-legacy infinity makes the V2 upgrade fail and leaves V1 data untouched; no silent repair. An isolated schema adds a Flyway schema marker, so SQL migration versions are counted separately. Each service's foundation cases reset disposable data independently; no sleep-based ordering or H2.

### Limits and acceptance gate

Established only storage-level guarantees. No proof yet of stock conservation/non-overselling, expiry-after-lock eligibility, real service-to-service timeout/replay behavior, actual charge/refund effects, cross-service evidence validation, safe public terminal transitions, full lease renewal/worker scheduling, crash recovery or notification dispatch. These remain C/D/E/F/G gates. Schema-supported states and synthetic chosen outcome metadata are not executed business effects. No performance/research conclusion is claimed.

Existing Mockito dynamic-agent/Jackson deprecation warnings remain non-failing. Manual databases/roles and UTC JVM launch remain prerequisites outside Testcontainers. No blocking application issue is currently identified; B acceptance belongs to the user. Recommended next checkpoint is C: Inventory counters/allocation and reservation lifecycle with authoritative expiry/terminal races, after explicit authorization. M1.3 as a whole is not complete.

### Working-tree file inventory (includes preserved A documentation)

```text
AGENTS.md
README.md
contracts/checkout-v1.json
contracts/inventory-v1.json
contracts/notification-v1.json
contracts/payment-v1.json
docs/ARCHITECTURE.md
docs/DECISIONS.md
docs/M1_3_TRANSACTION_DESIGN.md
docs/PROJECT_SPEC.md
docs/ROADMAP.md
services/checkout-service/src/main/java/io/aegis/checkout/domain/Workflow.java
services/checkout-service/src/main/java/io/aegis/checkout/infrastructure/persistence/WorkflowRepository.java
services/checkout-service/src/main/resources/db/migration/V2__transaction_foundations.sql
services/checkout-service/src/test/java/io/aegis/checkout/CheckoutApplicationTest.java
services/checkout-service/src/test/java/io/aegis/checkout/FoundationDatabase.java
services/checkout-service/src/test/java/io/aegis/checkout/FoundationPersistenceTest.java
services/inventory-service/src/main/java/io/aegis/inventory/domain/Reservation.java
services/inventory-service/src/main/java/io/aegis/inventory/infrastructure/persistence/ReservationRepository.java
services/inventory-service/src/main/resources/db/migration/V2__transaction_foundations.sql
services/inventory-service/src/test/java/io/aegis/inventory/FoundationDatabase.java
services/inventory-service/src/test/java/io/aegis/inventory/FoundationPersistenceTest.java
services/inventory-service/src/test/java/io/aegis/inventory/InventoryApplicationTest.java
services/payment-simulator/src/main/java/io/aegis/payment/domain/RefundRecord.java
services/payment-simulator/src/main/java/io/aegis/payment/domain/SyntheticPolicy.java
services/payment-simulator/src/main/java/io/aegis/payment/infrastructure/persistence/PaymentFoundationRepository.java
services/payment-simulator/src/main/resources/db/migration/V2__transaction_foundations.sql
services/payment-simulator/src/test/java/io/aegis/payment/FoundationDatabase.java
services/payment-simulator/src/test/java/io/aegis/payment/FoundationPersistenceTest.java
services/payment-simulator/src/test/java/io/aegis/payment/PaymentApplicationTest.java
services/payment-simulator/src/test/resources/fixtures/payments.sql
```

### Final B result and acceptance checklist

Final source declares READ COMMITTED explicitly on every new transaction primitive; enclosing caller transactions must use that same isolation. A final `.\mvnw.cmd -B verify` after this declaration passed **231 tests, 0 failures, 0 errors, 0 skips**, all five reactor projects successful (logs/m1.3-b-final-verified.log). It produced all four executable Java21 Boot JARs with current migrations and no test fixtures. Independent packaging `.\mvnw.cmd -B -pl services/checkout-service,services/inventory-service,services/payment-simulator -am '-DskipTests' package` also passed; tests were intentionally not executed by that packaging-only command (logs/m1.3-b-independent-package.log). Subsequent contract-description clarification passed dedicated validation again (logs/m1.3-b-final-contracts.log); schema shapes and required fields are unchanged.

| Service | Retained baseline tests | New PostgreSQL foundation tests | Total | Failures/errors/skips |
|---|---:|---:|---:|---|
| Checkout | 62 | 23 | 85 | 0/0/0 |
| Inventory | 31 | 18 | 49 | 0/0/0 |
| Payment | 34 | 24 | 58 | 0/0/0 |
| Notification | 39 | 0 | 39 | 0/0/0 |
| Total | 166 | 65 | 231 | 0/0/0 |

Final checks: exact six phase headings/25 milestone lines, local Markdown links/fences and git diff --check; all V1 contents unchanged; parent POM/wrapper/ignore and Notification implementation unchanged; exactly five implemented M1.2 handlers, eight reserved M1.3 operations, no clients/workers/Compose/cloud additions. No public DTO schema shape/status/enum changed; CD-2 remains deferred. Isolated validation-tool/cache/logs/target artifacts stay ignored. Application runtime role grants remain external and service-local, not administrator credentials. These checks do not establish distributed orchestration or stock/payment business execution.

- [x] Accepted A baseline and B-only authorization recorded; existing local documentation preserved.
- [x] Three additive V2 migrations and service-local repository/domain primitives implemented.
- [x] Canonical replay/conflict, identity/policy guards, rollback, migration upgrades and role tests pass.
- [x] All166 baseline regressions plus65 new tests pass; final reactor/packaging and dedicated specification validation pass.
- [x] No unimplemented mutation routes, remote calls, workers, outcome seeds or later execution behavior added.
- [x] Known limits and future correctness gates recorded; no experiment result fabricated.
- [ ] User acceptance of M1.3-B.
- [ ] Explicit authorization for C; M1.3 overall remains incomplete.
