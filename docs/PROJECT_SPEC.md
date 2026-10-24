# Aegis project specification

Status: M1.3-A/B accepted for progression; M1.3-C Inventory lifecycle implemented, verified (311 reactor tests), pending user acceptance. Payment execution, Checkout orchestration and autonomous recovery remain unimplemented and unauthorized. See D-018 in DECISIONS.md for C evidence and limitations.

## 1. Problem and purpose

A local slowdown in a dependency can consume upstream workers, connections, and queue capacity, producing cascading degradation. Independently configured reactive resilience mechanisms may respond poorly to correlated failures and moving bottlenecks.

Aegis will investigate dependency-aware telemetry, predictive risk estimation, and coordinated safety-constrained interventions. The synthetic checkout workload is an experimental environment, not a commercial application. The project has equally important engineering-learning and research goals.

## 2. Objectives and questions

**Approved engineering objectives:** learn Java backend engineering, concurrency, transaction correctness, distributed failure semantics, observability, reliability, adaptive control, and applied ML through a locally reproducible system.

**Research hypotheses, not established facts:** predictive risk estimates and dependency-aware coordination may improve resilience relative to conventional and adaptive reactive baselines. They may also add overhead, false alarms, or harmful actions.

Research questions:

- Can telemetry predict a defined degradation event with useful lead time on held-out scenarios?
- Does dependency information improve predictions or action selection?
- Does prediction improve successful throughput, latency, recovery, or availability beyond tuned reactive policies?
- What are the costs of false positives, intervention frequency, telemetry, and inference?
- How do safety constraints affect harmful actions and achievable benefit?
- Do results generalize across workloads, failure types, and bottlenecks?

## 3. Approved scope and constraints

- Four services: Checkout, Inventory, Payment Simulator, and Notification.
- One SKU per purchase with positive integer quantity; fixed catalog pricing and one configured currency, initially INR. Inventory is the authoritative catalog and price source.
- Synthetic payment operations only; no real money or external payment provider.
- No user-initiated cancellation initially.
- Successful payment after reservation expiry requires refund and reconciliation; do not silently reserve replacement stock.
- Explicit unresolved payment outcomes and incomplete compensation remain visible.
- Notifications are outside the critical purchase path.
- Java 21, Spring Boot, Maven, PostgreSQL, eventual Docker Compose deployment.
- One PostgreSQL server with separate databases and restricted roles per service. This is logical ownership, not physical resource isolation.
- Free, open-source tools preferred; no required paid services. AWS requires separate approval and verified free usage eligibility.
- Preserve the exact [roadmap](ROADMAP.md); authorize milestones individually.

**Deferred/non-goals:** shipping, tax, promotions, real payments, multi-region operation, production commerce features, multi-SKU purchases, authentication platform, and high availability. No Kafka, Redis, Kubernetes, or service mesh is justified initially.

## 4. Functional requirements

The requirements below describe eventual behavior; the current foundation/Inventory/Payment/Notification implementation and evidence are listed separately in README and DECISIONS.

| ID | Requirement | Earliest relevant milestone / evidence |
|---|---|---|
| FR-01 | Validate purchase input; expose purchase creation and durable status contracts. | M1.2 contracts/local persistence; M1.3 coordinated behavior |
| FR-02 | Reserve, query, commit, release, and expire stock without overselling. | M1.2 boundaries; M1.3 invariant and race tests |
| FR-03 | Simulate charge/decline, query outcomes, and simulate idempotent refunds. | M1.2 boundaries; M1.3 duplicate/uncertainty tests |
| FR-04 | Persist orchestration progress and recover incomplete operations. | M1.3 service failure tests; M1.4 restart tests |
| FR-05 | Store notification receipts with concurrent-safe semantic replay (201 new, 200 identical, 409 conflict); later deliver from durable Checkout intent. | Receipt API/deduplication M1.2; Checkout intent/delivery M1.3; system outage tests M1.4 |
| FR-06 | Distinguish critical synchronous, degradable synchronous, and non-critical background traffic. | Architecture now; workload realization requires later authorization |
| FR-07 | Collect correlated telemetry and run controlled failure scenarios. | Phase 2 |
| FR-08 | Evaluate conventional and adaptive reactive resilience baselines. | Phase 3 |
| FR-09 | Validate and execute bounded control actions with auditability and safe fallback. | Phase 4 |
| FR-10 | Train/evaluate offline models and integrate suitable Java inference. | Phase 5 |
| FR-11 | Run reproducible comparative experiments and report uncertainty and negative results. | Phase 6 |

## 5. Traffic classes and research workload

**Approved distinction:** critical synchronous operations preserve purchase correctness; degradable synchronous operations may return explicitly reduced quality; non-critical background work may be delayed. Concrete optional endpoints below are **proposed**, not newly approved service features.

| Class | Examples | Permitted treatment in future experiments |
|---|---|---|
| Critical synchronous | Purchase admission, inventory commands, charge/refund commands, authoritative outcome queries | Explicitly reject new work before acceptance when overloaded. After acceptance, preserve durable work and return honest pending/unavailable outcomes; never silently drop mutations or recovery obligations. |
| Degradable synchronous | Proposed advisory stock preview or optional purchase-summary enrichment | Omit optional enrichment or return clearly labeled stale/unknown advisory data under a reviewed contract. Never use it to authorize stock or charge decisions. |
| Non-critical background | Notification delivery | Delay/retry with visible backlog and bounded resources; do not delay purchase confirmation. |

Recovery, expiry, and compensation may run in background workers but are **correctness-critical**, not expendable notification traffic. Protect their progress when designing future scheduling policies.

Future workloads should vary arrival rate, traffic mix, hot-SKU contention, dependency latency, and failure duration. Admission control can reject unaccepted work; load shedding can remove optional work; concurrency limits can contain dependency pressure. None of these mechanisms is implemented or authorized in M1.2-A. Their policies and evaluation belong to Phase 3 onward.

Measure each class separately: admitted/rejected/completed purchases, authoritative versus degraded responses, notification backlog, and unresolved/compensating workflows. Rejection must not appear as a successful purchase or disappear from denominators. Notification delivery is not purchase throughput. Preserve identical safety rules across baselines.

## 6. Non-functional requirements

| ID | Requirement | Future verification |
|---|---|---|
| NFR-01 | Conserve stock, prevent duplicate logical charges, and prevent unsupported terminal-state claims. | Invariants in [ARCHITECTURE.md](ARCHITECTURE.md); real PostgreSQL tests |
| NFR-02 | Remain correct with overlapping requests and multiple workers/instances. | Concurrent duplicate, last-unit, worker-claim, and state-transition tests |
| NFR-03 | Recover durable unfinished work after crashes. | Controlled failure points in M1.3; process restart tests in M1.4 |
| NFR-04 | Bound remote waits and resource use without silently losing accepted work. | Deadline, pool exhaustion, queue/backlog tests when mechanisms are authorized |
| NFR-05 | Run locally with documented prerequisites and no cloud dependency. | Clean local Compose run in M1.4 |
| NFR-06 | Reproduce experiments with versions, configurations, seeds, reset procedures, and resource budgets. | M1.4 local reproducibility; later experiment manifests |
| NFR-07 | Keep business rules separate from telemetry, control, and inference. | Dependency review and module checks at the corresponding milestones |
| NFR-08 | Use synthetic data and keep experimental/admin controls local and explicit. | Deployment and endpoint exposure review |

No numerical performance target or measured improvement is asserted. Targets require a documented environment and measurements. Eventual completion assumes dependencies recover, durable state survives, and reconciliation is scheduled; safety must hold even when progress is unavailable.

## 7. Acceptance criteria

**M1.1: completed and accepted by the user.** Six consistent documentation artifacts reviewed; approved/proposed/deferred decisions distinguished; exact roadmap retained; no application artifacts.

**Phase 1:** locally runnable service system, validation and persistence tests, demonstrated invariants under duplicates/concurrency/uncertain outcomes, recovery evidence, and repeatable end-to-end execution. These are future gates, not passed checks.

**Research:** reproducible comparisons against tuned conventional and adaptive reactive baselines, identical correctness constraints and resource budgets, held-out evaluation, intervention/overhead reporting, and uncertainty analysis. A neutral or negative result can satisfy research acceptance.

## 8. Assumptions, risks, and open questions

Assume crash/network failures rather than malicious services; durable PostgreSQL survives application restart. Database loss and Byzantine behavior are separate fault classes. Exact clock/deadline semantics need explicit tests.

Risks: payment success racing expiry; compensation stalled by outages; retry amplification; worker overlap; shared PostgreSQL contention; workload simplicity limiting external validity; telemetry overhead; stale controller state; model leakage; unfair baseline tuning; and overclaiming generality from one machine.

**Deferred questions:** exact reservation duration and maximum workflow age; retry/deadline budgets; idempotency retention and client key scope; catalog values (INR currency approved); manual intervention policy; PostgreSQL/Maven patch pins; optional degradable endpoint contract; experiment workload mix and statistical protocol. Resolve each before its implementation, not by silent defaults.

See [DECISIONS.md](DECISIONS.md) for status and [ROADMAP.md](ROADMAP.md) for authorization boundaries.

## 9. Accepted M1.2 execution scope

The user accepted M1.2, including integrated E and the F audit, for progression; A/B are accepted and C Inventory execution is now explicitly authorized. Other later execution requires explicit authorization. Contracts live in [Checkout](../contracts/checkout-v1.json), [Inventory](../contracts/inventory-v1.json), [Payment](../contracts/payment-v1.json), and [Notification](../contracts/notification-v1.json). Contract labels describe the review snapshot; current implementation status is reported in README.

M1.2 acceptance requires four independently startable applications; Inventory catalog reads against PostgreSQL; Notification creation and idempotent replay under concurrent duplicates; Checkout/Payment query behavior using test fixtures; passing service-level tests and Flyway migrations; and no cross-service database access. At M1.2, purchase submission, reservation commands, payment/refund execution, recovery and compensation were M1.3-reserved contracts. C now implements only the Inventory reservation operations. Do not populate production databases with fictional completed purchases/payments.

Approved implementation choices: Spring MVC, Spring JDBC, service-owned Flyway SQL, Maven reactor, four independent applications, no shared Java implementation library, OpenAPI 3.x, `/api/v1`, UUID resources, RFC 9457-style Problem Details, Inventory price authority, INR minor units, and Testcontainers PostgreSQL tests. The documented Java 21/Boot baseline remains subject to actual build verification.

M1.2 schemas must not contain refund, recovery, reconciliation, or reservation-state-machine machinery. Automated tests use disposable Testcontainers databases. Manual development requires separately provisioned local databases and restricted credentials; complete reproducible deployment is M1.4.


## 10. M1.2-D verified scope

Payment GET reads only its database and preserves definitive versus pending state semantics. Notification POST/GET stores a synthetic receipt with PostgreSQL-enforced concurrent idempotency; neither STORED nor a receipt validates a purchase. Two service-owned V1 migrations add only payment and notification_receipt. There are no production payment outcome fixtures. Restricted roles, independent configuration, real PostgreSQL concurrency/outage tests and regression verification passed; see D-013 in DECISIONS. D was subsequently accepted for E progression. Payment/Checkout execution remains unimplemented; C adds Inventory reservation operations only.

## 11. M1.2-E implementation scope

Checkout now reads a durable service-local purchase representation through GET only. Its eleven fields preserve nullable authoritative amount/currency and the approved state/step/flag matrix. One V1 purchase table enforces static row invariants, including ordered finite timestamps. Runtime permissions are SELECT-only; migration ownership is separate. No production purchase fixtures, commands, state transitions, remote lookups, workers, idempotency tables or compensation/recovery machinery exist. Test-only snapshots exercise representations without asserting distributed evidence. Historical E verification is recorded in D-014; subsequent audit/acceptance and the design-only authorization are recorded in D-015/D-016.

## 12. M1.3-A design proposal

The [transaction design](M1_3_TRANSACTION_DESIGN.md) proposes explicit Saga invariants and evidence-based transitions while preserving existing public states. The refined design is accepted for B foundations, not implemented Saga capability. B addresses Payment finite timestamps and dedicated specification validation. Conditional recovery progress is not a guarantee under permanent outage or failed compensation. No numerical research result or new feature is claimed.

## M1.3-B actual implementation boundary

B stores identity, immutable payload/policy, snapshots, unresolved obligations and claim metadata using service-owned V2 migrations/JDBC repositories. No migration seeds transaction outcomes and no V1 was rewritten. Tests verify storage constraints/uniqueness/rollback/fencing, not an executed Saga. At B, stock conservation/expiry linearization, remote evidence validation, finalization, charge/refund execution and crash recovery were unverified future work. C is now explicitly authorized for Inventory only; see the current C section below. The six phases and 25 milestone names are unchanged.

## M1.3-C Inventory implementation boundary

C adds the four accepted reservation handlers, immutable admission/outcome arbitration, conserved available/reserved/sold balances and local READ COMMITTED lifecycle transactions. PostgreSQL row locks serialize lifecycle decisions; fresh database time after both reservation and catalog waits governs expiry. GET returns persisted state and never cleans up. Commands lazily materialize expiry, committing reclamation before an expiry conflict is returned. No autonomous expiry scheduler exists; eventual idle-stock reclamation remains conditional on commands until F.

V3 preserves V1/V2, initializes accounting from existing catalog balances and backfills retained negative identities. It deliberately refuses pre-existing V2 reservation snapshots because B did not allocate their stock: a reviewed accounting conversion is required, not fabricated history. See D-018 for exact files, tests and limits. This checkpoint does not implement Payment execution, Checkout submission/orchestration, remote coordination or recovery. No M1.3-C acceptance is implied.
