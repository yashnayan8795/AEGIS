# Aegis architecture decisions

Current status (2026-10-18): M1.1/A accepted; B foundation verified before C; Inventory C verification passed, awaiting user review. D and later remain unauthorized. Earlier sections record historical review states; D-012 records current implementation evidence.

**Approved** means accepted design baseline, not implemented capability. **Proposed** means a recommendation awaiting review. **Deferred** means intentionally unresolved until a relevant authorized milestone. Current explicit user authorization permits M1.2-B foundation and then M1.2-C Inventory implementation, subject to successful foundation verification. M1.2-D/later remain unauthorized. Earlier documentation-only restrictions below are historical; D-012 supersedes them for B/C.

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
