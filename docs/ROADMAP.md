# Aegis roadmap

Status: M1.3-A/B accepted for progression; M1.3-C Inventory lifecycle implemented, verified (311 reactor tests), pending user acceptance. Payment execution, Checkout orchestration and autonomous recovery remain unimplemented and unauthorized. See D-018 in DECISIONS.md for C evidence and limitations.

## Approved roadmap

### Phase 1 — Foundations and Core Application
- M1.1: Project specification and repository setup.
- M1.2: Core microservices.
- M1.3: Transaction workflow and correctness.
- M1.4: Local deployment and integration testing.

### Phase 2 — Observability and Failure Engineering
- M2.1: OpenTelemetry instrumentation.
- M2.2: Prometheus and Grafana monitoring.
- M2.3: Fault injection.
- M2.4: Reproducible failure scenario catalog.

### Phase 3 — Conventional Resilience and Baselines
- M3.1: Timeouts, retries, and circuit breakers.
- M3.2: Concurrency control and load shedding.
- M3.3: Benchmarking framework.
- M3.4: Adaptive reactive baseline.

### Phase 4 — Aegis Adaptive Control Plane
- M4.1: Telemetry aggregation and system state.
- M4.2: Deterministic policy engine.
- M4.3: Safety validation.
- M4.4: Intervention execution.
- M4.5: Closed-loop adaptive controller.

### Phase 5 — Predictive Intelligence
- M5.1: Dataset generation and feature engineering.
- M5.2: Model development and evaluation.
- M5.3: Java model inference.
- M5.4: Predictive policy integration.

### Phase 6 — Research Validation and Productization
- M6.1: Experiment management and React dashboard.
- M6.2: Comprehensive benchmarking.
- M6.3: Research analysis and ablation studies.
- M6.4: Final packaging and documentation.

## Phase 1 boundaries and exit evidence

These boundaries refine delivery scope without changing milestone names or order.

| Milestone | Scope | Entry dependency | Required exit evidence |
|---|---|---|---|
| M1.1 | Only README, AGENTS, PROJECT_SPEC, ARCHITECTURE, ROADMAP, DECISIONS | User documentation authorization | Completed: six-document review package accepted by the user |
| M1.2 | Service scaffolding, API contracts, local persistence, validation, service-level tests | Accepted M1.1 and explicit M1.2 authorization | Independently startable services, validated local operations, migrations/persistence tests, no cross-service SQL |
| M1.3 | Durable orchestration, idempotency, compensation, reconciliation, concurrency, recovery | Stable M1.2 contracts/local operations and explicit authorization | Unit/real-PostgreSQL concurrency and failure tests for agreed invariants; crash-point recovery evidence |
| M1.4 | Docker Compose deployment, end-to-end tests, restart testing, reproducibility | Correct M1.3 workflow and explicit authorization | Repeatable local startup, documented prerequisites/reset procedures, end-to-end and process restart evidence |

M1.2 may specify idempotency keys and operation IDs in contracts but must not implement M1.3 coordination mechanisms prematurely. The approved exception is concurrent-safe notification receiver replay (201 new, 200 identical, 409 conflict), which is explicitly M1.2. Local persistence tests may use Testcontainers in M1.2; this does not authorize Compose or system-wide testing early. Basic finite transport deadlines are hygiene when HTTP calls are introduced; configurable resilience policies remain in Phase 3.

## M1.1 documentation work plan (completed historical plan)

| Step | Purpose / deliverable | Learning | Acceptance / verification | Dependencies / risks |
|---|---|---|---|---|
| 1 | Record approved scope and remaining questions | Requirements versus hypotheses | Decisions have explicit status | User refinements; ambiguity risk |
| 2 | PROJECT_SPEC with requirements and future evidence | Safety, liveness, research validity | Trace requirements to milestones/invariants | Step 1; avoid performance promises |
| 3 | ARCHITECTURE with ownership, traffic, diagrams, recovery | Uncertain outcomes and local transactions | Walk normal, timeout, expiry, crash, compensation paths | Step 2; hidden failure obligations |
| 4 | DECISIONS with sources and trade-offs | Version compatibility and decision records | Official compatibility verified; no invented build result | Steps 1–3; moving documentation |
| 5 | ROADMAP preserving exact milestone text | Incremental delivery | Compare six phases and 25 milestones with brief | Approved roadmap; scope creep |
| 6 | README and AGENTS reflecting actual state | Honest documentation and authorization | No runnable claims; explicit milestone gate | Other documents; contradictory status |
| 7 | Consistency report for user review | Traceability | Check links, statuses, roadmap, invariants, file allowlist | All steps; M1.1 subsequently accepted and completed |

## Later dependency gates

Phase 2 requires a reproducible Phase 1 workload. Phase 3 requires observable, cataloged failures. Phase 4 requires defensible reactive baselines and validated action safety before closed-loop control. Phase 5 requires suitable data, leakage-resistant evaluation, and a safe deterministic controller. Phase 6 requires stable experiment configurations and comparable implementations. Detailed later plans are deferred until their design reviews; this is not authorization to implement them.

Research success does not require positive predictive gains. It requires valid comparisons, reproducible evidence, uncertainty reporting, and honest limitations.

## M1.2 checkpoints and executable acceptance

| Checkpoint | Scope / expected artifacts | State |
|---|---|---|
| M1.2-A | Four OpenAPI contracts; update six baseline docs and accepted decisions | Accepted for progression by subsequent B/C authorization |
| M1.2-B | Parent reactor, wrapper, four independent Spring Boot foundations | Accepted for progression; foundation verified |
| M1.2-C | Inventory catalog persistence/read API and service tests | Accepted by user; reactor/PostgreSQL tests passed |
| M1.2-D | Payment query persistence and Notification receipt/replay APIs with service tests | Accepted for progression by explicit E authorization |
| M1.2-E | Checkout query persistence/API using test fixtures | Accepted for progression; integrated on main 7fe74c1 |
| M1.2-F | Complete service-level verification, migration checks, accurate documentation | Audit executed 2026-10-21; M1.2 accepted for progression with follow-ups retained |

Eventual M1.2 acceptance: four independently startable services, Inventory lookup against PostgreSQL, Notification creation and concurrent-safe replay, Checkout/Payment query behavior using test fixtures, successful service-level tests/migrations, and no cross-service database access. No production completed-purchase/payment seed data. Automated PostgreSQL tests use Testcontainers; manual prerequisites are documented, while complete Compose provisioning remains M1.4.

Historical A authorization did not permit implementation. Subsequent B/C/D and then explicit E authorizations permit those checkpoints only; the user subsequently authorized the F audit and accepted M1.2 for M1.3-A design-only progression. Minimal M1.2 schemas exclude refund/recovery tables, reconciliation workers, and reservation state machines. At M1.2 all future M1.3 endpoints were contract-only reservations; C now implements the four Inventory reservation operations only.

## M1.3 subdivisions and authorization

The [design proposal](M1_3_TRANSACTION_DESIGN.md) suggests A design; B reviewed contracts/prerequisites; C Inventory; D Payment/refunds; E Checkout durable progression; F recovery/dispatch; G correctness audit. These are subdivisions of existing M1.3, not additional milestones or authorization to implement them. A/B are accepted for progression; C alone is now authorized for Inventory lifecycle implementation. D and later need separate authorization. B completed finite Payment timestamps and dedicated specification validation; later execution/recovery still requires its own correctness evidence.

## M1.3-B actual implementation boundary

B stores identity, immutable payload/policy, snapshots, unresolved obligations and claim metadata using service-owned V2 migrations/JDBC repositories. No migration seeds transaction outcomes and no V1 was rewritten. Tests verify storage constraints/uniqueness/rollback/fencing, not an executed Saga. At B, stock conservation/expiry linearization, remote evidence validation, finalization, charge/refund execution and crash recovery were unverified future work. C is now explicitly authorized for Inventory only; see the current C section below. The six phases and 25 milestone names are unchanged.

## M1.3-C Inventory implementation boundary

C adds the four accepted reservation handlers, immutable admission/outcome arbitration, conserved available/reserved/sold balances and local READ COMMITTED lifecycle transactions. PostgreSQL row locks serialize lifecycle decisions; fresh database time after both reservation and catalog waits governs expiry. GET returns persisted state and never cleans up. Commands lazily materialize expiry, committing reclamation before an expiry conflict is returned. No autonomous expiry scheduler exists; eventual idle-stock reclamation remains conditional on commands until F.

V3 preserves V1/V2, initializes accounting from existing catalog balances and backfills retained negative identities. It deliberately refuses pre-existing V2 reservation snapshots because B did not allocate their stock: a reviewed accounting conversion is required, not fabricated history. See D-018 for exact files, tests and limits. This checkpoint does not implement Payment execution, Checkout submission/orchestration, remote coordination or recovery. No M1.3-C acceptance is implied.
