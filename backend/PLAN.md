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
* [x] **Checkpoint 1.1: Relational Data Topology**
  * Map high-integrity relational structures for Users, Accounts, Transactions, and Ledger Entries via JPA/Hibernate object-relational mapping.
  * Establish structural constraints, database-level tracking fields, and strict relationship cascades.
* [x] **Checkpoint 1.2: Base Persistence Layer**
  * Implement Spring Data Repositories inside `com.wallet.gojo.ledger.repository` to abstract physical database access.
  * Enforce state immutability by completely omitting mutable state balance fields from the schema, ensuring balances are purely compiled states.
* [ ] **Checkpoint 1.3: Double-Entry Transaction Engine**
  * Build the core service layer that handles fund movements.
  * Enforce the Multi-Currency Zero-Sum Invariant check at the service boundary ($\sum \text{Debits} - \sum \text{Credits} = 0$ per currency asset class).
  * Enforce system invariants: Prevent standard customer accounts from ever dropping below a zero balance.
  * Validate transactional rollbacks: Ensure that if a credit write fails, the corresponding debit write is entirely purged via `@Transactional`.
  * **Add an independent Audit Verifier Service:** A system background process that proves the mathematical sum of every ledger entry balances out to zero.

### 🟨 Phase 2: Isolation & Concurrency Safety (The Race-Condition Shield)
* [ ] **Checkpoint 2.1: Pessimistic Locking Strategy**
  * Integrate database-level transactional locks (`SELECT FOR UPDATE`) to block multiple execution threads from evaluating or modifying the same account balances simultaneously.
  * Implement a deterministic resource sorting sequence (e.g., always locking the smaller Account ID first) to entirely prevent database deadlocks.
* [ ] **Checkpoint 2.2: Concurrency Stress Verification**
  * Write rigorous multithreaded Java unit tests (using `CountDownLatch` or `ExecutorService`) simulating thousands of rapid, simultaneous balance extractions and cross-transfers on a single account node to prove zero balance leakage.

### 🟩 Phase 3: Edge Security, Idempotency & Caching (The Gateway Layer)
* [ ] **Checkpoint 3.1: JWT Authentication & Route Security**
  * Introduce stateless JWT verification filters using Spring Security to securely validate user identities before reaching the ledger.
* [ ] **Checkpoint 3.2: Idempotency Middleware Integration**
  * Construct an API interceptor layer that monitors incoming payloads for an `X-Idempotency-Key`.
  * Tie this key to Redis to reject or return deduplicated responses for identical client requests within a 5-minute window, eliminating double-billing.
* [ ] **Checkpoint 3.3: Write-Through Caching Architecture**
  * Integrate Redis caching for rapid account balance reads, keeping it synchronized exclusively upon successful database commits to prevent dirty reads.

### 🟦 Phase 4: Event-Driven Decomposition (The Distribution Layer)
* [ ] **Checkpoint 4.1: Message Broker Integration**
  * Spin up Apache Kafka to serve as our asynchronous event backbone.
* [ ] **Checkpoint 4.2: Asynchronous Event Dispatching**
  * Hook into Spring's transactional lifecycle listeners to emit a `TransactionSettledEvent` *only* after the database transaction successfully commits.
* [ ] **Checkpoint 4.3: Decoupled Notification Microservice**
  * Build a completely standalone Spring Boot module that consumes transaction events from Kafka to generate real-time processing logs without adding latency to the main transaction engine.

### 🟪 Phase 5: Productionization & Test Isolation (The Deployment Validation)
* [ ] **Checkpoint 5.1: Containerized Infrastructure**
  * Orchestrate Docker Compose configuration scripts to spin up dependencies (PostgreSQL, Redis, Kafka) locally via single commands.
* [ ] **Checkpoint 5.2: Real-Environment Integration Testing**
  * Refactor automation test suites to use **Testcontainers** so your integration tests run against real, ephemeral instances of Postgres and Kafka during compilation.

### 🎨 Phase 6: The Real-Time Frontend (The Executive Dashboard)
* [ ] **Checkpoint 6.1: Next.js Foundation & Secure Authentication**
  * Scaffold the Next.js frontend and establish secure JWT state management using HTTP-Only cookies.
* [ ] **Checkpoint 6.2: Real-Time Command Dashboard**
  * Build an interactive UI utilizing WebSockets or SSE to instantly stream transaction history, dynamic wallet balances, and ledger auditing logs.
* [ ] **Checkpoint 6.3: Client-Side Idempotency Safeguards**
  * Implement robust UI button state locks and automatic client-side UUID generation to intercept duplicate submissions at the glass layer.