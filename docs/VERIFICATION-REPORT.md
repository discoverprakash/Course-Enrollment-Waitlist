# Verification Report

**Scope:** Approved requirements, HLD, LLD, test plan, Maven configuration, and current Java implementation. This report distinguishes automated evidence from pending manual CLI checks.

## 1. Verification Environment

Values below were captured from commands executed in the project root on Windows 11.

| Item | Observed value |
|---|---|
| Java | OpenJDK 17.0.19, Temurin 17.0.19+10 (`java --version`) |
| Maven | Apache Maven 3.9.16 (`mvn --version`) |
| Git commit | `293f358b7477fb6f469515eaf17d2bb916b3236a` (`git rev-parse HEAD`) |
| Working tree | `git status --short --branch`: branch `main...origin/main`; modified `README.md`; untracked `.gitignore`, assignment PDF, `docs/`, `pom.xml`, `project.zip`, `requirement.md`, and `src/`. This status was captured before creating this report; the report itself is also a new untracked file. |

## 2. Automated Test Results

Command: `mvn clean test`

**Result: BUILD SUCCESS** — 53 tests, 0 failures, 0 errors, 0 skipped.

| Test class | Executed |
|---|---:|
| `CourseEnrollmentCliTest` | 11 |
| `CourseOfferingStateTest` | 6 |
| `CourseEnrollmentServiceTest` | 30 |
| `InMemoryOfferingStoreTest` | 6 |
| **Total** | **53** |

Coverage is not reported: `pom.xml` does not configure JaCoCo, and this `mvn clean test` run generated no coverage report. No coverage percentages are inferred.

Coverage includes in-memory state initialization and immutability; capacity and membership validation; eligibility fixture lookup; enrollment/waitlisting and duplicate behavior; FIFO drop/promotion, eligibility rechecks, and failure atomicity; concurrent service operations; CLI parsing, output, and end-to-end enrollment-to-promotion behavior.

## 3. Focused Verification

Each exact command below ran the named JUnit method through Maven Surefire. The focused run count comes from the resulting Surefire XML; every selection executed at least one test.

| Scenario | Test class and method | Exact Maven command | Expected behavior and assertions actually checked | Executions / result |
|---|---|---|---|---:|
| Concurrent final-seat enrollment | `CourseEnrollmentServiceTest.concurrentDistinctEnrollmentsForLastSeatAllowExactlyOneEnrollment` | `mvn -q -Dtest=CourseEnrollmentServiceTest#concurrentDistinctEnrollmentsForLastSeatAllowExactlyOneEnrollment test` | Exactly one `ENROLLED`, one `WAITLISTED`; enrolled size equals capacity; one queued learner; enrolled/waitlist sets disjoint. | 1 / PASS |
| Concurrent duplicate enrollment | `CourseEnrollmentServiceTest.concurrentDuplicateEnrollmentCreatesOneMembershipWhenAvailableOrFull` | `mvn -q -Dtest=CourseEnrollmentServiceTest#concurrentDuplicateEnrollmentCreatesOneMembershipWhenAvailableOrFull test` | Available-seat calls yield one `ENROLLED` and one `ALREADY_ENROLLED`; full-course calls yield one `WAITLISTED` and one `ALREADY_WAITLISTED` at the same position; exactly one membership. | 1 / PASS |
| Duplicate enrolled request after eligibility change | `CourseEnrollmentServiceTest.duplicateEnrolledRequestReturnsExistingStateAfterEligibilityChanges` | `mvn -q -Dtest=CourseEnrollmentServiceTest#duplicateEnrolledRequestReturnsExistingStateAfterEligibilityChanges test` | After eligibility changes to false, retry returns `ALREADY_ENROLLED` and preserves the exact snapshot reference. | 1 / PASS |
| Duplicate waitlisted request | `CourseEnrollmentServiceTest.duplicateWaitlistedRequestReturnsPositionWithoutRecheckingEligibility` | `mvn -q -Dtest=CourseEnrollmentServiceTest#duplicateWaitlistedRequestReturnsPositionWithoutRecheckingEligibility test` | After eligibility changes to false, retry returns `ALREADY_WAITLISTED` at position 2 and preserves the exact snapshot. | 1 / PASS |
| FIFO promotion | `CourseEnrollmentServiceTest.dropPromotesFirstEligibleWaiterInFifoOrder` | `mvn -q -Dtest=CourseEnrollmentServiceTest#dropPromotesFirstEligibleWaiterInFifoOrder test` | Dropping the owner promotes the first eligible waiter and leaves the later waiters in order. | 1 / PASS |
| Skip and retain ineligible waiter | `CourseEnrollmentServiceTest.dropSkipsAndRetainsIneligibleWaiterWhilePromotingNextEligible` | `mvn -q -Dtest=CourseEnrollmentServiceTest#dropSkipsAndRetainsIneligibleWaiterWhilePromotingNextEligible test` | After the first waiter becomes ineligible, the next eligible waiter is promoted; the skipped learner remains queued in relative order. | 1 / PASS |
| All waiters ineligible | `CourseEnrollmentServiceTest.dropWithAllWaitersIneligibleLeavesSeatAvailable` | `mvn -q -Dtest=CourseEnrollmentServiceTest#dropWithAllWaitersIneligibleLeavesSeatAvailable test` | Drop succeeds without a promotee; enrollment becomes empty, seat remains available, and waitlist order is unchanged. | 1 / PASS |
| Enrollment prerequisite failure and recovery | `CourseEnrollmentServiceTest.prerequisiteEvaluationFailurePreservesStateAndRecovers` | `mvn -q -Dtest=CourseEnrollmentServiceTest#prerequisiteEvaluationFailurePreservesStateAndRecovers test` | Both open/full cases return `INTERNAL_ERROR` with the same snapshot; after recovery, another thread successfully enrolls or waitlists and state matches capacity. | 2 / PASS |
| Drop-time failure atomicity | `CourseEnrollmentServiceTest.dropEvaluationFailurePreservesSnapshotAndRecoversFromAnotherThread` | `mvn -q -Dtest=CourseEnrollmentServiceTest#dropEvaluationFailurePreservesSnapshotAndRecoversFromAnotherThread test` | Injected promotion lookup failure returns `INTERNAL_ERROR` and preserves the exact snapshot; after recovery, another thread drops successfully and promotes the next eligible learner. | 1 / PASS |
| Concurrent enrollment and drop | `CourseEnrollmentServiceTest.concurrentEnrollmentAndDropPreserveCapacityAndMembership` | `mvn -q -Dtest=CourseEnrollmentServiceTest#concurrentEnrollmentAndDropPreserveCapacityAndMembership test` | Concurrent operations finish with one eligible learner enrolled, no waitlist duplication, capacity maintained, and membership sets disjoint. | 1 / PASS |
| CLI end-to-end promotion | `CourseEnrollmentCliTest.completeEnrollmentWaitlistDropPromotionWorkflowRunsThroughCli` | `mvn -q -Dtest=CourseEnrollmentCliTest#completeEnrollmentWaitlistDropPromotionWorkflowRunsThroughCli test` | Captured CLI output contains three enrollments, waitlist position 1, drop/promotion result, and final status; final state has the promotee enrolled and empty queue. | 1 / PASS |

**Focused executions total: 12; all passed.**

## 4. Manual CLI Verification

**Status: PENDING.** The CLI was reviewed in source and exercised by automated stream-driven tests, but it was not manually launched and interacted with for this report. The following command and output expectations are based on the implementation; they are not claimed as observed manual output.

Launch from the project root:

```powershell
mvn package
java -cp target/classes com.courseenrollment.cli.Main
```

The demo fixture has eligible IDs `learner-a`, `learner-b`, `learner-c`, and `learner-d`; capacity is 3. Enter these commands manually:

```text
help
status
enroll learner-a
enroll learner-a
enroll learner-b
enroll learner-c
enroll learner-d
enroll learner-d
status
drop missing
drop learner-b
status
enroll bad id
help
exit
```

Expected result messages, based on `CourseEnrollmentCli`:

- `help` lists `enroll <learnerId>`, `drop <learnerId>`, `status`, `help`, and `exit`.
- Initial status shows capacity 3, 0 enrolled, 3 available, 0 waitlisted.
- First enrollment of `learner-a` is `ENROLLED`; its duplicate is `ALREADY_ENROLLED`.
- `learner-b` and `learner-c` are enrolled; `learner-d` is waitlisted at position 1; its duplicate reports position 1.
- Status then shows 3 enrolled, 0 available, 1 waitlisted.
- `drop missing` reports `UNKNOWN_LEARNER`; `drop learner-b` reports promotion of `learner-d`.
- Final status shows 3 enrolled, 0 available, 0 waitlisted. `enroll bad id` reports `INVALID_REQUEST`; the session continues to `help` and exits with `Goodbye.`.

Manually compare these outcomes and confirm end-of-input also exits with `Goodbye.` without an exception. Record that verification separately if completed.

## 5. Architecture and Requirement Compliance

- **Single offering/in-memory state:** `InMemoryOfferingStore` owns one configured offering and the eligibility fixture; the CLI demo uses capacity 3. There is no database or persistence across process restarts.
- **Immutable snapshots/atomic publication:** `CourseOfferingState` copies and exposes unmodifiable collections. Service mutations build a new state and call the store's atomic reference replacement once.
- **Shared lock:** Enrollment, drop, and status use the same service-level `ReentrantLock`, released in `finally` blocks.
- **Capacity and unique membership:** State construction enforces capacity, duplicate waitlist rejection, and no overlap between enrolled and waitlisted membership. Service checks duplicates before eligibility.
- **FIFO and eligibility:** Drop scans the ordered waitlist, rechecks fixture eligibility, retains skipped learners in order, and promotes only the first eligible learner. No eligibility-triggered rescan was added.
- **Failure atomicity:** Promotion evaluation failures return `INTERNAL_ERROR` before publication; failure-recovery tests assert the original snapshot is retained and a later operation succeeds.
- **Thin CLI:** `CourseEnrollmentCli` parses, dispatches, and formats results; business rules remain in `CourseEnrollmentService`.
- **Scope:** No frontend, HTTP layer, database, or additional runtime service was introduced. `OfferingStatus` is the approved read-only view.

No deviation from the approved HLD/LLD was identified. The in-memory state and fixture are intentionally lost at process exit.

## 6. Final Verification Summary

- **Total automated tests:** 53 in `mvn clean test`; focused selections separately executed 12 test invocations.
- **Automated result:** PASS; full suite 53 passed, 0 failed, 0 errors, 0 skipped; all focused selections passed.
- **Manual CLI:** PENDING; launch and comparison steps are listed above.
- **Coverage:** Not reported; JaCoCo is not configured in `pom.xml` and no coverage report was generated by the required build.
- **Known limitations:** State is in-memory and resets on process exit; prerequisite data is a fixed demo fixture; manual interactive CLI verification remains pending.
- **Outstanding issues/blockers:** No automated or architecture blocker identified. Complete the manual CLI check before treating verification as fully complete.
- **Final readiness:** **PENDING**, solely for manual CLI verification.


Manual Verification Report
================================

PS C:\Codebase\Course-Enrollment-Waitlist\Course-Enrollment-Waitlist> java -cp target\classes com.courseenrollment.cli.Main
> enroll
INVALID_REQUEST: Invalid command, arguments, or learner ID. Type help for command formats.
> drop
INVALID_REQUEST: Invalid command, arguments, or learner ID. Type help for command formats.
> enroll learner-a extra
INVALID_REQUEST: Invalid command, arguments, or learner ID. Type help for command formats.
> status extra
INVALID_REQUEST: Invalid command, arguments, or learner ID. Type help for command formats.
> help extra
INVALID_REQUEST: Invalid command, arguments, or learner ID. Type help for command formats.
> register learner-a
INVALID_REQUEST: Invalid command, arguments, or learner ID. Type help for command formats.
> enroll unknown-id
UNKNOWN_LEARNER: unknown-id is not in the learner eligibility fixture.
> First enroll learner-a
INVALID_REQUEST: Invalid command, arguments, or learner ID. Type help for command formats.
> enroll learner-a
ENROLLED: learner-a is enrolled.
> enroll learner-a
ALREADY_ENROLLED: learner-a is already enrolled.
> drop learner-a
DROPPED: learner-a dropped; no eligible learner was available, so the seat is open.
> drop learner-a
NOT_ENROLLED: learner-a has no active enrollment.
> status
Capacity: 3
Enrolled: 0
Available seats: 3
Waitlisted: 0
> help
enroll <learnerId>  Enroll an eligible learner or join the waitlist.
drop <learnerId>    Drop an enrollment and promote the first eligible waiter.
status              Show capacity and enrollment/waitlist counts.
help                Show command formats.
exit                End the session.
> exit
Goodbye.