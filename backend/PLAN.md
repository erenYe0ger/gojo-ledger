# Plan: Gojo-Ledger Core Backend

This document establishes the strategic architectural blueprint and functional checkpoints for **Gojo-Ledger**, an enterprise-grade, high-throughput, double-entry banking ledger engine. The backend is built natively using **Java 21** and **Spring Boot 3.x** inside `/backend/ledger`.

The core philosophy of Gojo-Ledger is absolute financial data integrity: **money is never mutated in place; it is only moved.**

---

## 🏗️ Architectural Foundations

Gojo-Ledger addresses the critical system demands prioritized by financial institutions:
* **Mathematical Proof (Immutable Ledger):** Implements absolute Double-Entry Accounting where every financial movement is recorded as balanced pairs of debits and credits.
* **Concurrency Isolation:** Guarantees zero balance slippage and race-condition immunity under hyper-concurrent transaction loads.
* **Idempotency Over Wire:** Ensures network retries or duplicated client payloads never result in duplicate financial execution.
* **Decoupled Side Effects:** Offloads high-latency processes (notifications, audit pipelines) into an asynchronous event stream.

---

## 🗺️ Functional & System Boundaries

### 1. Account & Legal Entity Domain
The system must model structural internal and external financial entities. It treats every participant as a collection of asset, liability, or equity accounts rather than a simple digital profile.
* **Multi-Account Profiles:** Support for internal customer wallets, merchant processing accounts, and settlement/clearing accounts.
* **Asset Categorization:** Accounts must enforce boundaries based on type (e.g., standard consumer accounts cannot carry negative balances, whereas corporate/system clearing accounts can execute short-term overdraft settlements).

### 2. Strict Double-Entry Core Engine
The core execution boundary rejects the naive model of a single `balance` field. Balances are derived states compiled from an immutable ledger stream.
* **Atomic Balances:** Every money movement creates one parent `Transaction` composed of at least two atomic `LedgerEntry` objects.
* **Zero-Sum Invariant:** For any transaction, the sum of debits must perfectly equal the sum of credits ($$\sum 	ext{Debits} - \sum 	ext{Credits} = 0$$). If this mathematical invariant fails, the entire operations block drops and rolls back completely.
* **State Immutability:** Once a ledger entry is written, it can never be updated or deleted. Reversals or corrections require issuing a brand-new, explicit counter-transaction.

### 3. Concurrency Protection & Synchronization
When a user spikes multiple simultaneous payment submissions, or millions of users hit a single merchant node simultaneously, the engine enforces strict isolation boundaries.
* **Row-Level Serialization:** Temporary operational padlocks must secure account balance states the millisecond an evaluation begins, preventing intersecting threads from reading or writing stale financial thresholds.
* **Deadlock Prevention Strategy:** System resources must be locked in a deterministic, globally uniform sorting sequence (e.g., always locking the lower Account ID first) to completely mitigate database thread lock deadlocks.

### 4. Idempotency Layer
Every transaction invocation from an external upstream system must carry a unique tracking token.
* **Replay Protection:** The system evaluates incoming requests against an ultra-fast, volatile storage lookup. If a token has been executed or is currently processing, subsequent requests are intercepted and served the identical prior result without touching the core database engine.
* **Window Management:** Idempotency tracking keys must possess deterministic expiration windows to prevent storage bloat while guaranteeing safety across common client retry windows.

### 5. Asynchronous Event Pipeline
The transaction path must remain clear of blocking, high-latency external dependencies.
* **Transactional Messaging Pattern:** Event publication must be tightly bound to the database state lifecycle. Notification vectors or processing logs must only fire outward once the core database ledger successfully commits the records to disk.
* **Event Streams:** Transaction execution signals are broadcasted into dedicated messaging pipelines, allowing downstream microservices to ingest details at their own pace without inducing backpressure on the core payment engine.

---

## 🏁 Implementation Checkpoints

### 🟥 Phase 1: Core Domain & Data Infrastructure (The Audit Foundation)
Focuses entirely on defining the database schema, entity structures, and the mathematical validation of the double-entry engine.
* [ ] **Checkpoint 1.1: Database Schema Architecture**
    * Design and deploy the baseline schema for Users, Accounts, Transactions, and Ledger Entries using a migration manager.
    * Establish structural constraints, composite indexes, and relational integrity.
* [ ] **Checkpoint 1.2: Base Persistence Layer**
    * Map JPA/Hibernate entities inside `com.wallet.gojo.ledger`.
    * Ensure that direct update capabilities on calculated balance fields are strictly blocked or omitted from accessible repositories.
* [ ] **Checkpoint 1.3: Double-Entry Transaction Engine**
    * Build the core service layer that handles fund movements.
    * Enforce the Zero-Sum Invariant check at the service boundary.
    * Validate transactional rollbacks: Ensure that if a credit write fails or throws an exception, the corresponding debit write is entirely purged from the system state.

### 🟨 Phase 2: Isolation & Concurrency Safety (The Race-Condition Shield)
Focuses on securing the system against race conditions, over-drafting, and concurrent thread collision.
* [ ] **Checkpoint 2.1: Pessimistic Locking Strategy**
    * Integrate database-level transactional locks to block multiple execution paths from modifying the same accounts simultaneously.
    * Implement deterministic sequence ordering for balance evaluations to prevent database deadlocks.
* [ ] **Checkpoint 2.2: Concurrency Stress Verification**
    * Develop comprehensive multi-threaded tests that simulate thousands of simultaneous balance extractions and transfers on a single account profile.
    * Verify that final balances match mathematical expectations and zero accounts drop below valid absolute limits.

### 🟩 Phase 3: Idempotency & High-Speed Cache (The Replay Defense)
Focuses on making the application resilient to network failures and introducing high-speed components to bypass database overhead for read paths.
* [ ] **Checkpoint 3.1: Idempotency Middleware Integration**
    * Construct an API interception mechanism that scans for unique tracking identifiers on incoming transfers.
    * Connect the middleware to an in-memory storage layer to track processing and completed transaction states.
    * Verify that repeated identical submissions receive immediate deduplicated responses without re-running ledger logic.
* [ ] **Checkpoint 3.2: Write-Through Caching Architecture**
    * Introduce high-speed cache caching for account balance lookups.
    * Sync cache evictions and write-updates strictly with successful transactional completions to avoid dirty reads.

### 🟦 Phase 4: Event-Driven Decomposition (The Distribution Layer)
Focuses on decoupling peripheral side-effects from the core transaction loop by turning the application into an event publisher.
* [ ] **Checkpoint 4.1: Message Broker Integration**
    * Introduce an enterprise-grade message streaming broker into the stack.
    * Configure abstract producers capable of serializing financial event payloads securely.
* [ ] **Checkpoint 4.2: Asynchronous Event Dispatching**
    * Hook into Spring's transactional lifecycle listeners to emit a `TransactionSettledEvent` only after the database commit successfully resolves.
* [ ] **Checkpoint 4.3: Decoupled Microservice Simulation**
    * Construct a completely standalone consumer module (e.g., Notification Service).
    * Ensure this module independently digests event data streams from the broker to trigger fake SMS/Email alerts without blocking the primary ledger thread.

### 🟪 Phase 5: Productionization & Test Isolation (The Deployment Validation)
Focuses on containerization, testing architecture, and preparing Gojo-Ledger for a true distributed deployment.
* [ ] **Checkpoint 5.1: Containerized Infrastructure**
    * Construct standard multi-stage build files for the Java application container.
    * Orchestrate configuration scripts to spin up dependencies (PostgreSQL, Redis, Kafka) locally via single commands.
* [ ] **Checkpoint 5.2: Real-Environment Integration Testing**
    * Refactor the automation test suites to utilize native test isolation utilities (such as dynamic ephemeral container systems).
    * Ensure the integration suite boots up and executes assertions against real, disposable instances of PostgreSQL, Redis, and Kafka during compilation.