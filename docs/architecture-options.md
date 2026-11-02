# Architecture Options: Course Enrollment and Waitlist

## Purpose and scope

This analysis uses [`requirement.md`](../requirement.md) as the source of truth. It assumes a greenfield Java project with one course offering, no starter code or data model, and the assignment's permitted in-memory implementation. It compares architecture choices without starting implementation.

The required behavior is intentionally narrow: enroll an eligible learner if capacity remains, otherwise add them to the FIFO waitlist; make duplicate requests return the existing enrollment or waitlist state; allow a dropped learner to request enrollment again as a fresh request; and, on a drop, promote the first currently eligible learner while retaining skipped ineligible learners in their original relative order. If nobody is eligible, the seat remains open. The drop and any promotion must be one consistent state transition: failure must not leave partial enrollment or queue changes.

## Capabilities Required by the Assignment

- One offering's enrollment, drop, prerequisite check, waitlist, and promotion workflow.
- Capacity and uniqueness invariants, accepted-request FIFO ordering, and the agreed policy for ineligible waitlisted learners.
- Clear operation results, including the existing waitlist position for a duplicate waitlist request.
- State consistency for each operation, especially atomic drop-and-promotion behavior.
- Focused automated tests, runnable setup instructions, a design specification, and an AI-use record with verification evidence.

The assignment permits an in-memory store. It does not require an HTTP API, database, persistence across restarts, frontend, authentication, deployment, multiple offerings, or a distributed system. These are not included in the recommended design.

## Option 1: In-Process Java Service with CLI and In-Memory State

### Structure

A small Java application with a domain/service layer, an in-memory repository or state object, and a thin command-line adapter. Keep request parsing and output formatting outside the enrollment logic. The service owns the business transitions; the repository holds the single offering's capacity, enrolled learners, and ordered waitlist.

A minimal package structure could separate `domain`, `service`, `store`, and `cli`. This is a structure suggestion, not a required set of classes or APIs.

### Technology and dependencies

- A supported Java JDK (for example, Java 17 or 21).
- Maven or Gradle for build and test execution.
- JUnit 5 for automated tests.
- No runtime framework or third-party persistence dependency is needed. Java collections and locking primitives are sufficient.

### Enrollment, capacity, and promotion

The service checks the learner's prerequisite eligibility before creating either an enrollment or a waitlist entry. It checks for an existing active enrollment or waitlist entry before accepting a new request and returns that existing state when found. If there is room, it enrolls the learner; otherwise, it appends a waitlist entry in accepted-request order.

On drop, the service computes the resulting state as one operation: remove the enrolled learner, scan waitlist entries in FIFO order for the first learner currently eligible, promote that learner if found, and retain every skipped ineligible learner in the same relative order. If none qualifies, the seat stays open. Prepare the resulting state before replacing the current state, or otherwise ensure a failure cannot expose intermediate mutations. For one offering, a single service-level lock can serialize these state transitions if calls may arrive from multiple threads.

A monotonically increasing acceptance sequence can make FIFO order deterministic even when requests arrive at effectively the same time. The order is based on request acceptance, not prerequisite eligibility changes or promotion attempts.

### Data storage and consistency

All state lives in process memory, such as a set or map of enrolled learner identifiers and an ordered collection of waitlist entries. The application loses state on exit; that is acceptable under the assignment's in-memory allowance. This is not a multi-process or multi-instance store.

The service should apply a complete transition or leave the previous state intact. If a drop/promotion calculation fails, do not persist a partially changed enrollment set or waitlist. A successful drop with no eligible waitlist candidate is a completed transition that removes the dropped learner and leaves one seat open, not a rollback case.

### Concurrency and testing

A CLI that processes one command at a time has naturally serialized requests. If the service is callable concurrently, guard the check-and-update transition with one lock for the offering; checking capacity outside the lock and mutating inside it would permit over-enrollment. Do not claim cross-process coordination from an in-process lock.

Test the service directly with JUnit: available and full capacity, prerequisite rejection, duplicate enrollment and waitlist requests, returned queue position, fresh position after dropping and re-requesting, FIFO promotion, skipping and retaining ineligible learners, no eligible candidate, and invalid/repeated drops. Add a failure-injection test around a drop/promotion transition to prove that an exception leaves the prior state unchanged. If concurrent calls are supported, add a focused test that races for the final seat and verifies capacity and uniqueness invariants.

### Tradeoffs and complexity

- **Advantages:** Smallest dependency footprint; direct fit for the assignment; fast, deterministic unit tests; business behavior is visible without framework indirection.
- **Disadvantages:** State is volatile; CLI is not a network API; in-process locking only handles threads in this process.
- **Complexity:** Low. The main correctness risk is mutating several collections before all parts of the transition have succeeded.

## Option 2: Java HTTP Service with In-Memory State

### Structure

A modular monolith with an HTTP adapter (controller/handler), the same domain/service boundary as Option 1, and an in-memory state implementation. The HTTP layer validates and maps requests/responses; it does not implement capacity or queue rules. Keep the service as the single owner of state transitions so it can also be called directly from tests.

### Technology and dependencies

- Java plus a web framework such as Spring Boot Web, or a smaller Java HTTP server/library.
- JUnit 5 for service tests; a framework test client for endpoint tests.
- No database is necessary for this option. The in-memory repository remains volatile.

### Enrollment, capacity, promotion, and storage

Use the same decision sequence and invariants as Option 1. The endpoint delegates enrollment and drop operations to the service. Waitlist positions are calculated from the current ordered queue, including retained ineligible learners. The single process stores state in memory and loses it on restart.

### Concurrency and testing

HTTP requests can be handled concurrently, so a lock or equivalent atomic state-transition boundary is required around all reads and writes that decide capacity, duplicate status, and queue order. For this single offering, one lock is simple and sufficient; it serializes the affected operations. Multiple server processes would each have different memory and would not share state, so this design must run as one instance to preserve a coherent offering state.

Test both the service rules and HTTP mapping. Use the same focused domain tests as Option 1, add endpoint tests for request validation and result mapping, and test a concurrent final-seat race if concurrent HTTP access is part of the chosen behavior. Keep the atomicity failure test at the service boundary; HTTP tests alone cannot establish that the domain transition is indivisible.

### Tradeoffs and complexity

- **Advantages:** Provides a callable service API if that is useful to a reviewer or client; retains a separable domain layer and in-memory simplicity.
- **Disadvantages:** Adds framework configuration and endpoint behavior not required by the assignment; state still disappears on restart and cannot be shared safely across multiple instances.
- **Complexity:** Low to medium, depending on framework. The API does not solve persistence or distributed concurrency.

## Option 3: Persistent Database-Backed Modular Monolith (Out of Current Scope)

### Structure

A Java application with an HTTP or CLI adapter, domain/service layer, repository abstraction, and relational database. A relational schema would represent the offering, enrollments, and waitlist entries, with a unique active learner/offering constraint and an explicit ordering key for waitlist acceptance.

### Technology and dependencies

A typical stack could use Spring Boot, JDBC or JPA, a relational database such as PostgreSQL, and JUnit 5. Database integration tests could use a temporary database instance. These choices introduce runtime and test dependencies absent from the minimal assignment.

### Enrollment, capacity, promotion, and storage

The database stores state across process restarts. A transaction must encompass dropping the learner, selecting and rechecking the first eligible waitlisted learner, and promoting that learner. Locking the offering row or using an equivalent serializable transaction prevents two requests from consuming the same seat. Constraints should backstop application checks for duplicate active state.

To preserve the skip-but-retain policy, promotion scans candidates in waitlist order and updates only the selected eligible learner. Ineligible entries retain their original ordering key. If none qualifies, commit the drop and leave the seat available. Any error before commit rolls back both the drop and promotion changes.

### Concurrency and testing

Database transactions and row-level locking can coordinate requests across application threads and instances, unlike an in-process lock. Tests should include service tests plus integration tests that exercise transaction rollback, uniqueness, capacity under competing requests, and FIFO promotion against the actual database behavior.

### Tradeoffs and complexity

- **Advantages:** Durable state and robust cross-thread/process coordination; database constraints can enforce important uniqueness rules.
- **Disadvantages:** More code, setup, dependencies, and integration-test burden; persistence and deployment concerns are not needed for this one-offering slice.
- **Complexity:** Medium to high. It is not the recommended assignment implementation: the requirements explicitly say not to add a new database unless the starter requires one, and this greenfield project has no starter requiring it.

## Comparison

| Criterion | Option 1: CLI + in-memory | Option 2: HTTP + in-memory | Option 3: Database-backed monolith |
|---|---|---|---|
| Maintainability | Good if CLI and rules are separated | Good with a thin HTTP adapter; more framework surface | Good for a growing service, but more moving parts to maintain |
| Simplicity | Highest | Medium | Lowest |
| Testability | Excellent for the bounded domain; no server needed | Excellent domain tests plus endpoint tests | Good, with extra integration-test setup |
| Correctness | Strong if transitions are staged/atomic and optionally locked | Strong within one process if all state transitions share a lock | Strong across instances when transaction and locking boundaries are correct |
| Concurrency handling | Sequential CLI or one in-process lock; no cross-process support | One lock handles threads in one instance; multiple instances are unsafe | Database transaction/locking can coordinate multiple instances |
| Assignment fit | Best | Viable only if an API is desired | Outside scope absent a changed requirement |
| Implementation complexity | Low | Low to medium | Medium to high |

## Required Capabilities vs. Optional Enhancements

**Required for this assignment:** implement the specified workflow and invariants; keep drop and promotion failure-atomic; choose the permitted in-memory store; produce focused tests and the required documentation and AI-use record. A lock is needed only if the chosen execution model can process requests concurrently; a sequential CLI does not need concurrent-request infrastructure.

**Optional and not required:** an HTTP/REST API, database or restart persistence, authentication, user interface, multiple offerings, background workers, a message broker, multiple service instances, metrics, deployment automation, and performance targets. They would add complexity without satisfying a missing assignment requirement. In particular, adding a new database conflicts with the stated scope unless a later requirement changes that boundary.

## Recommendation

Use **Option 1: an in-process Java service with a thin CLI and an in-memory store**. Keep the enrollment and promotion rules in a directly testable service, not in command parsing. Apply drop plus promotion as a staged or otherwise indivisible state update; use a single lock around the full state transition only if calls can be concurrent. This meets the assignment's explicit allowance, minimizes dependencies, keeps the core behavior easy to inspect, and supports focused tests for the most important invariants without adding infrastructure.

**Alternative worth considering:** Option 2 is reasonable if an HTTP service boundary is specifically desired for demonstration or integration. It can reuse the same domain service and in-memory store, but should remain a single process and should not be mistaken for durable or multi-instance storage. Option 3 should be revisited only if persistence or cross-instance operation becomes an explicit requirement.
