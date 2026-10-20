# Aegis contributor and agent instructions

## Current authorization

M1.1 and M1.2-A/B/C/D are accepted for progression. The user explicitly authorized M1.2-E Checkout only after baseline verification. Work on feature/m1.2-e-checkout from accepted local main. Implement only GET /api/v1/purchases/{purchaseId}, minimal service-owned JDBC/Flyway persistence, static snapshot constraints and service tests. Leave E changes uncommitted for review. Do not change contracts; report material contradictions. Preserve unrelated work.

Authorized paths: Checkout module and directly related documentation. Inventory, Payment, Notification and infrastructure remain regression baselines. Necessary local dependency resolution, Testcontainers and verification are permitted. No cloud, Compose, purchase POST, workflow transitions, remote calls, compensation/recovery workers or later checkpoint implementation. E must not be marked accepted before user review. M1.2-F and M1.3 require separate explicit authorization.

## Working method

1. Read the specification, architecture, roadmap, and decisions before changing behavior.
2. Before a major feature, explain the problem, design, alternatives, trade-offs, learning outcomes, and verification plan.
3. Work incrementally with small, reviewable changes. Do not silently alter the six-phase, 25-milestone roadmap.
4. Distinguish approved decisions, proposed refinements, deferred choices, and implemented capabilities. Architecture approval is not implementation authorization.
5. Prioritize the user's understanding. Explain consequential concurrency and recovery decisions in plain language.
6. Preserve unrelated work. Obtain explicit approval before destructive repository changes, history rewriting, deletion, or irreversible operations.

## Milestone boundaries

- M1.2: service scaffolding, API contracts, local persistence, validation, and service-level tests.
- M1.3: durable orchestration, idempotency, compensation, reconciliation, concurrency, and recovery.
- M1.4: Docker Compose deployment, end-to-end tests, restart testing, and reproducibility.

Notification receipt idempotency is explicitly approved for M1.2: new ID 201, identical semantic replay 200 with the original receipt, conflicting payload 409; database uniqueness must make this correct under concurrency. This specific exception does not authorize workflow idempotency or other M1.3 mechanisms.

Do not pull later mechanisms into earlier milestones. Defining a future contract is not permission to implement its workflow. Explain any prerequisite conflict and seek a scope decision before crossing the boundary.

## Engineering constraints

- Java 21 and Spring Boot for backend applications; Maven multi-module architecture is the planned build structure.
- Use Python only for offline ML training and appropriate experiments.
- Prefer free, open-source dependencies. Justify every new dependency.
- The complete system must eventually run locally through Docker Compose.
- Do not require paid APIs, commercial monitoring, managed cloud databases, or cloud accounts.
- AWS requires explicit approval and verification of free usage eligibility before introduction.
- Do not introduce Kafka, Kubernetes, Redis, or service meshes without a demonstrated need and reviewed decision.
- Separate domain behavior, transport/persistence adapters, telemetry, control policies, and ML inference.
- Services own their data; do not use cross-service SQL, shared entities, or service implementation dependencies.
- Keep database transactions local and short. Never hold one open across a remote call.
- Enforce shared-state invariants in PostgreSQL; in-process locks alone are insufficient.
- A timeout is an unknown outcome. Preserve stable operation identities and durable reconciliation obligations.
- Resilience policies must not bypass inventory, payment, compensation, or workflow invariants.

## Verification and evidence

When implementation is authorized, write meaningful unit, PostgreSQL integration, concurrency, and failure-handling tests at the appropriate milestone. Use real PostgreSQL for locking and constraint behavior. Test response loss, duplicate commands, crashes between steps, expiry races, and compensation failures. Prefer controlled synchronization over sleep-only race tests.

Use framework-managed testing dependencies. Verify official compatibility documentation before locking or changing versions; record sources and any justified override in `docs/DECISIONS.md`.

Run checks appropriate to the change and report what actually ran, failed, or could not run. Do not claim tests passed unless executed. For documentation-only work, check links, roadmap fidelity, terminology, decision status, scope, and implementation claims; do not create application tests.

Never fabricate measurements, performance gains, research results, or novelty claims. Separate hypotheses from observations. Record experiment configuration, versions, seeds, resource budgets, baseline tuning, and uncertainty when experiments are authorized.

Update documentation with behavior changes. README commands must work in the current repository; future commands must not be presented as available.

## Contract and checkpoint rules

Use OpenAPI 3.1 JSON under `contracts/` without a shared Java implementation library. Label all operations with milestone and implementation status. M1.3-reserved routes must not become M1.2 stub handlers. Use `/api/v1`, UUID resource identities, RFC 9457-style errors, Inventory price authority, INR and integer minor units. Contract examples are synthetic, not experimental results or production seeds.

Keep M1.2 persistence minimal: catalog, purchase query representation, payment query representation, and notification receipt only. No reservation state machines, refund/recovery tables, reconciliation workers, or orchestration. Future schema needs must be documented separately. Automated database tests use Testcontainers; full provisioning and Compose remain M1.4.

Checkpoint order: A design/contracts; B Maven/Spring foundation; C Inventory; D Payment/Notification; E Checkout; F verification/documentation. Obtain explicit approval before each next checkpoint. Current authorization permits M1.2-E only. F and later require separate explicit approval.
