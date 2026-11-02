# AI Usage Log

## 1. Purpose and Scope

This log records how GitHub Copilot assisted the Course Enrollment & Waitlist assessment across requirements, architecture, design, tests, implementation, verification, and future architecture analysis. It distinguishes direct evidence from reconstructed summaries and does not treat code presence or a passing test as proof that a particular AI suggestion was accepted.

The prompt source available for this review is the conversation transcript. The excerpts below are verbatim excerpts of user requests, not separate exported prompt records. Copilot outputs are summarized from the conversation and resulting artifacts; full response exports were not found in the repository. The repository has only the `Initial commit` (`293f358`); the current worktree is dirty, so Git history cannot attribute individual file edits or decisions to a particular contributor.

Decision labels:

- **ACCEPTED:** explicit user selection or approval is present.
- **MODIFIED:** explicit user feedback or review changed the AI-assisted proposal/artifact.
- **REJECTED:** an explicit rejection is documented.
- **PENDING CONFIRMATION:** no explicit acceptance/rejection can be verified from available evidence.

## 2. AI Tools Used

- **GitHub Copilot in VS Code chat** assisted with requirement analysis, architecture/design documents, test planning, Java source/tests, code review, and verification reporting.
- **Maven/JUnit 5** were used as independent execution evidence for automated tests; these tools are not AI.
- **Git and source/document inspection** provided environment, source, and history evidence.

No separately exported prompt/response log or `INDEPENDENT-CODE-REVIEW.md` was found. The history records the user prompts and Copilot responses in the ongoing chat, but the repository does not contain a per-file AI attribution record.

## 3. Development Workflow Summary

The work proceeded from a requirements review to independent architecture options, then approved HLD/LLD and a test plan. Implementation was requested incrementally: Java/Maven state/store foundation, enrollment/waitlisting, drop/promotion, then the interactive CLI. Automated tests were run after each relevant increment and at the final CLI stage. Later documentation covered verification and future production evolution.

The user made material decisions throughout: selected the in-memory CLI architecture, specified prerequisite and waitlist rules, approved the HLD/LLD, refined test setups, and requested a specific capacity invariant correction. Those explicit decisions are recorded below. Later implementation increments and documents have no explicit post-delivery acceptance message in the transcript available for this log, so their disposition remains pending confirmation.

## 4. Detailed AI Interaction Log

Each row aggregates a closely related set of chat exchanges for one development phase. Prompt cells contain a verbatim excerpt, not a reconstruction, unless explicitly labeled otherwise.

### Phase 1 — Requirements understanding

| ID | Prompt / Request | AI Suggestion or Output | Decision | Human Review or Modification | Verification Evidence |
|---|---|---|---|---|---|
| P1 | Verbatim excerpt: “Before we start designing anything, help me understand the requirements properly.” | Separated assignment requirements from suggestions; identified ambiguous prerequisites, duplicates, FIFO, ineligible-front behavior, and drop errors. | MODIFIED | User replied that the analysis looked good, then supplied explicit rules for prerequisite checks, duplicate responses, re-enrollment, FIFO acceptance order, skip-but-retain promotion, open seats, and no-change drops. Those choices superseded the suggested assumptions. | Final rules are in [`requirement.md`](../requirement.md); the original assignment criteria remain separated from agreed decisions. |

### Phase 2 — Architecture exploration

| ID | Prompt / Request | AI Suggestion or Output | Decision | Human Review or Modification | Verification Evidence |
|---|---|---|---|---|---|
| P2 | Verbatim excerpt: “Now I want you to explore possible architecture approaches for implementing this solution.” | Compared CLI/in-memory, HTTP/in-memory, and database-backed approaches; recommended the smallest in-process Java service with CLI and in-memory state. | ACCEPTED | User explicitly selected Option 1. HTTP, database, and multi-instance operation remained optional/out of scope. | [`docs/architecture-options.md`](architecture-options.md) records the comparison and recommendation; HLD preserves the selected three-component boundary. |

### Phase 3 — HLD and LLD

| ID | Prompt / Request | AI Suggestion or Output | Decision | Human Review or Modification | Verification Evidence |
|---|---|---|---|---|---|
| P3 | Verbatim excerpt: “Now I want to work on the high-level architecture before moving to detailed design or implementation.” | Proposed CLI, enrollment service, and in-memory store; added diagrams and documented locking, immutable candidate snapshots, and failure atomicity. Later drafted the minimal domain model and detailed enrollment/drop algorithms. | ACCEPTED | User said the HLD architecture/diagrams looked good and later explicitly approved the LLD. User also set fixture, capacity-3 demo, interactive-session, result-status, and publication semantics; these were recorded as approved decisions. | [`docs/HLD.md`](HLD.md) and [`docs/LLD.md`](LLD.md), including the approved class model, algorithms, transition matrix, and CLI contract. |

### Phase 4 — Test planning

| ID | Prompt / Request | AI Suggestion or Output | Decision | Human Review or Modification | Verification Evidence |
|---|---|---|---|---|---|
| P4 | Verbatim excerpt: “Please add a Business Validation and Edge-Case Matrix to `docs/LLD.md`.” Later: “Please make these final refinements” to the test plan. | Produced a JUnit matrix covering capacity, eligibility, duplicates, FIFO, failure injection, concurrency, and CLI behavior. | MODIFIED | User corrected test setup so waitlisted learners start eligible and eligibility changes happen only after joining; requested post-promotion position and recovery checks; downgraded output-stream failure to secondary. A duplicate malformed-drop test was also removed after the user identified redundant executions. | [`docs/test-plan.md`](test-plan.md); the remaining service test `dropMalformedLearnerIdsReturnInvalidRequestWithoutChangingState` is the single parameterized malformed-drop case. |

### Phase 5 — Implementation Increment 1

| ID | Prompt / Request | AI Suggestion or Output | Decision | Human Review or Modification | Verification Evidence |
|---|---|---|---|---|---|
| P5 | Verbatim excerpt: “Set up a minimal Java 17 Maven project with JUnit 5” and “Implement the immutable `CourseOfferingState` … and `InMemoryOfferingStore`.” | Added Maven/JUnit configuration, immutable state, eligibility fixture, atomic-reference store, and initial tests. | MODIFIED | During the explicitly requested review, the store replacement test revealed that publication could change the initialized offering capacity. The service/store increment was modified to reject capacity changes atomically, and the test was changed to cover rejection. | `InMemoryOfferingStore.publishState` and `InMemoryOfferingStoreTest.publishStateRejectsCapacityChangeWithoutReplacingSnapshot`; the focused store suite passed, followed by the clean suite during Increment 2. |

### Phase 6 — Implementation Increment 2

| ID | Prompt / Request | AI Suggestion or Output | Decision | Human Review or Modification | Verification Evidence |
|---|---|---|---|---|---|
| P6 | Verbatim excerpt: “Implement Increment 2: enrollment and waitlisting.” | Added `CourseEnrollmentService.enroll`, result statuses, ID validation, lock/candidate publication, and tests for duplicates, unknown/ineligible learners, concurrency, and evaluation failure recovery. | PENDING CONFIRMATION | No explicit post-increment acceptance/rejection message is present in the available transcript. The later request to proceed to Increment 3 is recorded as workflow progression, not treated here as a specific code-review approval. | The conversation records `mvn clean test`: 27 tests passed at that increment; subsequent final runs include the service tests. Source: `CourseEnrollmentService.java`, `EnrollmentResult.java`, `CourseEnrollmentServiceTest.java`. |

### Phase 7 — Implementation Increment 3

| ID | Prompt / Request | AI Suggestion or Output | Decision | Human Review or Modification | Verification Evidence |
|---|---|---|---|---|---|
| P7 | Verbatim excerpt: “Implement Increment 3: drop and FIFO waitlist promotion.” | Added `drop`, `DropResult`, FIFO eligibility scan, skip-but-retain behavior, atomic publication, and concurrency/failure-recovery tests. | PENDING CONFIRMATION | No explicit post-increment acceptance/rejection message is present in the available transcript. | The conversation records `mvn clean test` and JaCoCo runs passing after the drop increment. Current evidence is in `CourseEnrollmentService.java`, `DropResult.java`, and `CourseEnrollmentServiceTest.java`; no commit-level attribution exists. |

### Phase 8 — Implementation Increment 4

| ID | Prompt / Request | AI Suggestion or Output | Decision | Human Review or Modification | Verification Evidence |
|---|---|---|---|---|---|
| P8 | Verbatim excerpt: “lets move to next incremental phase : interactive CLI and end-to-end tests.” | Added stream-injectable CLI, command parsing/result formatting, demo `Main`, status view, and CLI end-to-end tests. | PENDING CONFIRMATION | No explicit post-increment acceptance/rejection message is present in the available transcript. | The conversation records `mvn clean test`: 53 tests passed after CLI work. Sources: `CourseEnrollmentCli.java`, `Main.java`, `OfferingStatus.java`, `CourseEnrollmentCliTest.java`. |

### Phase 9 — Independent verification and review

| ID | Prompt / Request | AI Suggestion or Output | Decision | Human Review or Modification | Verification Evidence |
|---|---|---|---|---|---|
| P9 | Verbatim excerpt: “Before moving to Increment 2, please review the actual source code and tests against docs/LLD.md.” Later: “Create `docs/VERIFICATION-REPORT.md`…” | Reviewed state/store/tests, found the capacity-changing publication issue, fixed it with a regression test, ran full/focused Maven commands, and generated JaCoCo reports using an ad-hoc Maven goal. | PENDING CONFIRMATION | The user authorized changes only for genuine correctness issues; the capacity invariant change was made and tested. The final verification report was requested, but no explicit acceptance of that report is in the transcript. No separate independent review file is present. | Recorded commands include `mvn clean test` (53 passed after CLI) and 11 focused selections/12 executions (all passed). Current [`docs/VERIFICATION-REPORT.md`](VERIFICATION-REPORT.md) contains a “Manual Verification Report” transcript, but its provenance cannot be attributed through Git. It shows a partial CLI session; it does not evidence the complete waitlist-promotion workflow being manually exercised. |

### Phase 10 — Documentation and future enhancements

| ID | Prompt / Request | AI Suggestion or Output | Decision | Human Review or Modification | Verification Evidence |
|---|---|---|---|---|---|
| P10 | Verbatim excerpt: “Create `docs/FUTURE-ENHANCEMENTS.md` with the following structure” and “Create a professional, evidence-based document named `docs/AI-USAGE-LOG.md`…” | Produced a production evolution assessment covering persistence, distributed concurrency, overload, HA/DR, observability, security, deployment, failure scenarios, and phased migration. The current request asks for this AI-use log. | PENDING CONFIRMATION | No explicit user review/acceptance of the future-enhancements document is available. The AI-use log has not yet been reviewed. | [`docs/FUTURE-ENHANCEMENTS.md`](FUTURE-ENHANCEMENTS.md); no code or POM changes were made for this phase. |

## 5. Key Engineering Decisions and Tradeoffs

| Decision | Evidence and rationale | Trade-off / scope |
|---|---|---|
| Choose Java CLI and in-memory storage for the assessment | User explicitly selected Option 1 after the options comparison; `requirement.md` permits the minimal in-memory fallback. | Simple and testable, but volatile and single-process; not a production persistence choice. |
| Use immutable `CourseOfferingState` and `AtomicReference` publication | HLD/LLD specify complete candidate snapshots. Review found capacity could change during publication, so the store/test were modified to preserve offering capacity. | Strong local snapshot boundary; the reference is not shared across replicas. |
| Use one service-level lock | Approved HLD/LLD and `CourseEnrollmentService` serialize enrollment, drop, and status within this JVM. | Prevents same-instance races, but reduces same-offering parallelism and gives no cross-process coordination. |
| Check duplicate membership before eligibility | Approved rule returns existing enrollment/waitlist status even when a learner's fixture eligibility later changes; tests assert unchanged snapshot. | Avoids retroactively changing existing state on a retry; eligibility is rechecked only during promotion. |
| Skip but retain ineligible waiters; promote on successful drop only | Explicit user decisions and service tests define FIFO scan, current eligibility recheck, retained order, and open-seat behavior. | A learner who becomes eligible later can remain waiting until a later successful drop; unrelated enrollment does not rescan. |
| Publish only after eligibility and candidate preparation | LLD requires one complete transition; injected-failure tests check snapshot identity and later recovery. | A pre-commit failure has no state effect; a lost response after commit requires idempotency in any future API. |
| Keep CLI thin | Approved HLD/LLD; CLI parser formats service outcomes and does not implement business rules. | Fits the assessment; does not provide a network integration boundary. |
| Defer distributed components | User selected the minimal option; future document treats PostgreSQL as the first production correctness step and Redis/broker/multi-region as conditional. | Avoids unnecessary assessment complexity; production availability/scaling needs would require a separate design. |

## 6. AI Suggestions Accepted, Modified, or Rejected

- **ACCEPTED (2 phase-level interactions):** Option 1 was explicitly selected; the three-component HLD and LLD were explicitly approved.
- **MODIFIED (3 phase-level interactions):** Initial requirement assumptions were replaced with user-specified business decisions; test fixtures/matrix were revised per user corrections; Increment 1 publication behavior/test were changed after the requested review exposed capacity mutation.
- **REJECTED (0 explicitly recorded):** No explicit rejection of a Copilot suggestion is documented. HTTP/database options were evaluated but not selected for this assignment; they are not represented as a recorded rejection.
- **PENDING CONFIRMATION (5 phase-level interactions):** Post-delivery acceptance of Increment 2, Increment 3, Increment 4, the verification report, and the future-enhancements document is not explicit in the accessible transcript. Their implementation or existence is not treated as proof of approval.

Counts are phase-level log dispositions (10 rows), not a count of every individual chat turn. Multiple refinements are aggregated within their phase rows.

## 7. Independent Verification of AI-Generated Outputs

- Requirements and design artifacts were checked against the assignment and the explicit user decisions; the test plan and LLD were refined when setup assumptions or duplicate tests were identified.
- `mvn clean test` was run and recorded as passing 53 tests with 0 failures, 0 errors, and 0 skipped after CLI implementation. Focused Maven selections covered 11 scenarios with 12 executions; all passed.
- Focused service tests include final-seat concurrency, concurrent duplicate enrollment, FIFO promotion, skip/retain, all-ineligible open-seat behavior, pre-publication evaluation failures, and recovery on another thread.
- CLI tests use injected readers/writers and assert command results and an end-to-end enrollment/waitlist/drop/promotion workflow.
- A JaCoCo report was generated by an ad-hoc Maven invocation during development; the POM does not configure JaCoCo, and the final verification report does not claim coverage metrics for its `mvn clean test` run.
- Current `docs/VERIFICATION-REPORT.md` says manual CLI verification is PENDING but also contains an appended manual transcript. The transcript demonstrates malformed inputs, unknown learner, one enrollment/duplicate, drop, status, help, and exit. It does **not** show a full waitlist-to-promotion manual session. Its actor/provenance is not established by Git history. Do not claim Copilot manually ran it.
- Git history contains only `293f358 Initial commit`; the working tree contains uncommitted/untracked project files. It cannot provide phase-by-phase author attribution.

## 8. Challenges, Corrections, and Lessons Learned

- **Capacity mutation found in review:** The initial store replacement test used a new capacity, revealing that snapshots could replace the offering configuration. The store now rejects capacity changes during atomic replacement; a dedicated regression test was added and the suite passed.
- **Waitlist fixture assumptions corrected:** The user required learners to be eligible when accepted onto the waitlist; tests now change eligibility only after queue entry. This avoids testing an invalid business setup.
- **Duplicate test removed:** Two parameterized malformed-drop tests had identical inputs/assertions. One was removed, saving five executions; the focused service suite passed afterward.
- **Build environment issue resolved:** Maven was initially not on the active terminal PATH. User-level Maven/Java paths were configured, after which Maven commands executed successfully. This was environment setup, not an application-code change.
- **Manual evidence needs careful wording:** A sample workflow in README is not proof of manual execution. The current verification report includes a partial transcript, but does not evidence the full promotion workflow.

These examples show why generated code, tests, and prose were checked against the approved invariants and run outputs rather than accepted on appearance alone.

## 9. AI Usage Limitations

- AI-generated code and tests require human review against requirements and concurrency/failure invariants.
- Passing tests do not guarantee correctness; the current tests do not exhaust all schedules, runtime failures, or production integrations.
- Concurrency tests exercise selected races and may not expose every possible race or starvation condition.
- AI may suggest unnecessary abstractions or out-of-scope infrastructure; the user repeatedly constrained work to the approved CLI/in-memory architecture.
- AI-generated documentation must be checked against the final implementation, POM, test reports, and actual command output. The manual-verification provenance discrepancy is left explicit.
- The developer retains responsibility for engineering decisions, final assessment claims, security, and submission accuracy.

## 10. Final Reflection

Copilot contributed across the workflow by structuring alternatives, drafting design artifacts, implementing small increments, and helping review/test them. The strongest evidence of independent judgment is the user's explicit selection and refinement of requirements/architecture, the requested source review that found a real capacity invariant issue, correction of test assumptions and duplicate coverage, and reliance on actual Maven output for verification. The system remains intentionally an assessment-scale in-memory CLI, not a production distributed service.

## 11. Evidence References

- [`requirement.md`](../requirement.md) — assignment behavior and approved business decisions.
- [`README.md`](../README.md) — current project summary, run instructions, demo fixture, and documented example workflow.
- [`docs/architecture-options.md`](architecture-options.md) — evaluated architecture options and chosen minimal approach.
- [`docs/HLD.md`](HLD.md) and [`docs/LLD.md`](LLD.md) — approved components, state model, algorithms, CLI and consistency rules.
- [`docs/test-plan.md`](test-plan.md) — traceable service, concurrency, failure, and CLI test plan.
- [`docs/VERIFICATION-REPORT.md`](VERIFICATION-REPORT.md) — environment, automated results, focused runs, and supplied manual transcript.
- [`docs/FUTURE-ENHANCEMENTS.md`](FUTURE-ENHANCEMENTS.md) — future production evolution assessment.
- [`pom.xml`](../pom.xml) — Java 17 and JUnit 5 configuration; no JaCoCo plugin.
- [`CourseEnrollmentService.java`](../src/main/java/com/courseenrollment/service/CourseEnrollmentService.java), [`CourseOfferingState.java`](../src/main/java/com/courseenrollment/domain/CourseOfferingState.java), [`InMemoryOfferingStore.java`](../src/main/java/com/courseenrollment/store/InMemoryOfferingStore.java), and [`EligibilityFixture.java`](../src/main/java/com/courseenrollment/store/EligibilityFixture.java) — business behavior, immutable state, store, fixture.
- [`CourseEnrollmentCli.java`](../src/main/java/com/courseenrollment/cli/CourseEnrollmentCli.java) and [`Main.java`](../src/main/java/com/courseenrollment/cli/Main.java) — interactive adapter and demo entry point.
- [`CourseEnrollmentServiceTest.java`](../src/test/java/com/courseenrollment/service/CourseEnrollmentServiceTest.java), [`CourseEnrollmentCliTest.java`](../src/test/java/com/courseenrollment/cli/CourseEnrollmentCliTest.java), [`CourseOfferingStateTest.java`](../src/test/java/com/courseenrollment/domain/CourseOfferingStateTest.java), and [`InMemoryOfferingStoreTest.java`](../src/test/java/com/courseenrollment/store/InMemoryOfferingStoreTest.java) — actual JUnit coverage.
- Git: `git log --oneline` shows only `293f358 Initial commit`; current modifications are not committed.
- Prompt provenance: verbatim user-request excerpts in this log are taken from the accessible Copilot chat transcript. No separate prompt export was found.

## 12. Items Requiring Developer Confirmation

1. Confirm or amend the PENDING dispositions for Increment 2, Increment 3, Increment 4, the verification report, and the future-enhancements document. This log deliberately does not infer approval from code presence or later workflow progression.
2. Confirm who performed the manual CLI transcript currently appended to `docs/VERIFICATION-REPORT.md`, and whether a complete manual enroll → waitlist → drop → promotion session was run. The available transcript does not show that full workflow.
3. If assessment submission requires exact full prompts and full Copilot response excerpts rather than verbatim request excerpts and concise response summaries, export/attach the relevant chat records. No separate prompt/response archive is available in the repository.
4. Confirm whether any post-initial-commit changes were made manually or by another contributor; the current Git history has no commits to establish attribution.
