# Focused Test Plan: Course Enrollment and Waitlist

## Scope and Trace Keys

This plan is based on [`requirement.md`](../requirement.md) and [`LLD.md`](LLD.md). It targets the selected Java CLI, enrollment service, and in-memory store only. It defines test cases; it does not implement tests or prescribe a build tool.

Requirement keys `FR1`-`FR5` refer to the five functional requirements in `requirement.md`. Invariant keys are from the LLD:

- **I1 Capacity:** enrolled count never exceeds positive capacity; an open seat is valid when no eligible waiter is found.
- **I2 Exclusive membership:** each learner has at most one active enrollment or waitlist entry; no duplicates.
- **I3 Eligibility:** new enrollment, waitlisting, and promotion require current eligibility.
- **I4 FIFO:** accepted queue order is preserved; skipped ineligible entries retain relative order.
- **I5 Atomicity:** publish a complete candidate state or leave the previous state unchanged.
- **I6 No-change outcomes:** invalid, unknown, ineligible, duplicate, or failed operations do not mutate state.
- **I7 Lock lifecycle:** release the offering lock on every result/failure path.

**Essential** cases cover assignment acceptance criteria and approved behavior. **Secondary** cases verify approved behavior beyond the first working implementation. **Optional** cases are useful hardening but are not required to demonstrate the scoped feature.

## Service Test Matrix

Use JUnit 5 service tests for business behavior. Unless a case says otherwise, assert the result and compare the complete pre/post offering state. After every successful mutation, run the shared invariant assertions described below.

### Initialization and input validation

| Priority and JUnit 5 case | Setup | Action | Expected result and state change | Trace |
|---|---|---|---|---|
| Essential: `initialState_usesConfiguredPositiveCapacityAndIsEmpty` | Initialize sample store at capacity 3. | Read status. | Capacity is 3; enrolled, waitlist, and available counts are 0, 0, and 3. No memberships exist. | Approved initialization; I1, I2. |
| Essential: `initialState_rejectsNonPositiveCapacity` | Try capacity 0 and a negative capacity. | Initialize offering. | Initialization is rejected; no usable offering state is created. | LLD positive-capacity invariant; I1. |
| Essential: `enroll_invalidLearnerIds_returnsInvalidRequest` | Parameterize null, empty, blank, and a string containing whitespace. Capture state. | Call service enrollment directly, bypassing CLI validation. | `INVALID_REQUEST`; exact state unchanged. | Approved service-boundary validation; I5, I6. |
| Essential: `drop_invalidLearnerIds_returnsInvalidRequest` | Same malformed-ID values; capture state. | Call service drop directly. | `INVALID_REQUEST`; exact state unchanged. | Approved service-boundary validation; I5, I6. |
| Essential: `enroll_unknownLearner_returnsUnknownLearner` | Valid one-token ID absent from the fixture; offering has space. | Enroll learner. | `UNKNOWN_LEARNER`, not `INELIGIBLE`; no enrollment or queue change. | Approved CLI/result contract; FR1, I3, I6. |
| Essential: `enroll_knownIneligible_returnsIneligible` | Known fixture entry is false; test both open and full offerings. | Enroll learner. | `INELIGIBLE`; learner is neither enrolled nor waitlisted. | FR1, FR2, FR4; I2, I3, I6. |

### Enrollment, waitlisting, duplicates, and re-enrollment

| Priority and JUnit 5 case | Setup | Action | Expected result and state change | Trace |
|---|---|---|---|---|
| Essential: `enroll_eligibleLearnerWithAvailableSeat_enrolls` | Capacity 2, empty state, known eligible learner. | Enroll learner. | `ENROLLED`; learner appears once in enrollment; one seat is consumed. | FR1; I1-I3, I5. |
| Essential: `enroll_eligibleLearnerWhenFull_appendsToWaitlist` | Capacity 1, one enrolled learner, empty queue, new learner eligible. | Enroll new learner. | `WAITLISTED` at position 1; append exactly once; capacity unchanged. | FR2; I1, I2, I4, I5. |
| Essential: `enroll_duplicateEnrolled_returnsExistingAfterEligibilityChanges` | Enroll a known eligible learner, then change its fixture value to false between completed operations. | Enroll the same learner again. | `ALREADY_ENROLLED`; eligibility change does not remove or duplicate the enrollment. State unchanged on retry. | Approved duplicate precedence; I2, I6. |
| Essential: `enroll_duplicateWaitlisted_returnsCurrentPositionAfterEligibilityChanges` | Capacity 1 is full; A and B are eligible when they join the waitlist at positions 1 and 2. Change A to ineligible after acceptance. | Enroll A again. | `ALREADY_WAITLISTED` at position 1; queue and membership unchanged. | Approved duplicate precedence; I2, I4, I6. |
| Essential: `enroll_duplicateWaitlisted_returnsUpdatedPositionAfterEarlierPromotion` | Capacity 1 is full; A and B are eligible when they join at positions 1 and 2. | Drop the enrolled owner, promoting A; then enroll B again. | `ALREADY_WAITLISTED` at B's current position 1; queue remains `[B]`, with no duplicate entry. | FR2-FR3; approved current-position response; I2, I4-I6. |
| Essential: `enroll_droppedLearnerGetsFreshWaitlistPosition` | Capacity 1; owner enrolled, A and B eligible when they join the waitlist. Drop owner so A is promoted and B remains queued. | Re-enroll former owner while full. | `WAITLISTED` at the tail, after B; no active duplicate membership. | Approved fresh-request decision; FR1-FR2; I2, I4. |
| Essential: `enroll_openSeatDoesNotRescanRetainedWaitlist` | Capacity 1; A is eligible when waitlisted behind the owner. Change A to ineligible after acceptance, then drop the owner; the drop leaves a seat open and retains A. Change A back to eligible between operations. | Enroll a different known eligible learner B. | B is enrolled in the open seat; A remains waiting at the same position. Enrollment does not trigger promotion or rescan. | Approved drop-only promotion/no-rescan decision; FR1; I1-I4. |

### Drop, prerequisite rechecks, and FIFO promotion

| Priority and JUnit 5 case | Setup | Action | Expected result and state change | Trace |
|---|---|---|---|---|
| Essential: `drop_promotesFirstEligibleAndRetainsSkippedWaiters` | Capacity 1; owner enrolled; A, B, and C are eligible when they join in that order. After acceptance, change A to ineligible; B and C remain eligible. | Drop owner. | `DROPPED`, B promoted. A and C remain queued in relative order `[A, C]`. | FR3-FR4; approved skip-but-retain; I1-I5. |
| Essential: `drop_rechecksEligibilityAndPromotesWaiterWhoBecameEligible` | Capacity 1; owner enrolled; A and B are eligible when they join in that order. After acceptance, change A to ineligible, then back to eligible before the drop. | Drop owner. | A is promoted as the earliest currently eligible waiter; B remains queued. | FR3-FR4; approved promotion-time eligibility check; I3-I5. |
| Essential: `drop_skipsWaiterWhoBecameIneligibleAndRetainsPosition` | Capacity 1; owner enrolled; A and B queued while eligible. Change A to ineligible and keep B eligible. | Drop owner. | B is promoted; A remains at its original relative position. | FR3-FR4; approved skip-but-retain; I3-I5. |
| Essential: `drop_allWaitersIneligible_leavesSeatOpen` | Capacity 1; owner enrolled; A and B are eligible when they join the waitlist. Change both to ineligible after acceptance. | Drop owner. | `DROPPED` without promotee; enrollment decreases, seat remains open, queue `[A, B]` is unchanged. | FR3-FR4; approved no-eligible behavior; I1, I4-I6. |
| Essential: `drop_unknownLearner_returnsUnknownLearner` | Valid ID absent from fixture and not enrolled. | Drop learner. | `UNKNOWN_LEARNER`; no state change. | Approved unknown-learner outcome; I6. |
| Essential: `drop_knownLearnerWithoutEnrollment_returnsNotEnrolled` | Known learner is not enrolled (may be waitlisted). | Drop learner. | `NOT_ENROLLED`; if waitlisted, its queue entry remains; no promotion occurs. | FR5; approved repeated-drop behavior; I2, I6. |
| Essential: `drop_repeatedDropDoesNotPromoteAnotherWaiter` | Capacity 1; owner enrolled; A and B are eligible when they join the waitlist. | Drop owner, then repeat the same drop. | First call promotes A; second returns `NOT_ENROLLED`. B remains waiting; second call has no state effect. | FR3, FR5; I2, I5, I6. |
| Essential: `getStatus_returnsConsistentAggregateWithoutMutation` | Any state with known capacity, enrollment, and queue. | Read status. | Correct capacity/enrolled/available/waitlist counts; state and fixture unchanged; no promotion. | LLD status contract; I1-I2, I5. |

### Concurrency and failure injection

| Priority and JUnit 5 case | Setup | Action | Expected result and state change | Trace |
|---|---|---|---|---|
| Essential: `concurrentDistinctEnrollmentsForLastSeat_onlyOneGetsSeat` | Capacity 1, empty state; two distinct known eligible learners. Use a start barrier/latch so both service calls contend. | Enroll both concurrently. | Exactly one `ENROLLED`, one `WAITLISTED`; enrolled count is 1, queue has one unique learner, no capacity violation. Which learner wins is nondeterministic. | FR1-FR2; approved lock; I1-I5, I7. |
| Essential: `concurrentDuplicateEnrollment_createsOneMembership` | Run two same-ID requests concurrently, parameterized for one available seat and a full offering. | Submit both enrollment calls together. | With a seat: one `ENROLLED`, one `ALREADY_ENROLLED`. When full: one `WAITLISTED`, one `ALREADY_WAITLISTED` with the same position. Exactly one active membership in either case. | FR2; approved duplicate behavior; I2, I4-I5, I7. |
| Essential: `enroll_prerequisiteEvaluationFailure_doesNotCommitAndRecovers` | Known learner is eligible; inject a lookup failure for evaluation. Cover both an open seat and a full offering. | Enroll learner; after `INTERNAL_ERROR`, restore normal fixture evaluation between operations, then retry from a different thread. | First call leaves snapshot unchanged. Retry succeeds as `ENROLLED` or `WAITLISTED` according to capacity; invariants hold. The bounded follow-up proves lock release. | Approved failure outcome; I5-I7. |
| Essential: `drop_promotionEvaluationFailure_preservesStateAndRecovers` | Owner enrolled; A, B, and C are eligible when they join in order. Change A to ineligible after acceptance; inject a lookup failure for B. | Drop owner. After `INTERNAL_ERROR`, verify the full original snapshot, restore normal evaluation between operations, then retry drop from a different thread. | First call leaves owner enrolled and queue `[A, B, C]` unchanged. Retry drops owner, skips A, promotes B, and leaves `[A, C]`; invariants hold and the bounded follow-up proves lock release. | FR3-FR5; approved failure-atomic promotion; I4-I7. |

For failure injection, use a test-controlled fixture/map whose lookup throws for a selected learner. Change fixture values only between completed service calls. No production policy engine or new runtime component is needed. The failure should occur before `publishState` so the test verifies the specified rollback boundary.

## CLI Test Matrix

Use the actual CLI with in-memory service/store for session and output tests. A small spy/test double may verify that malformed commands never invoke the service; it is test-only and does not change the production component structure.

| Priority and JUnit 5 case | Setup | Action | Expected result and state change | Trace |
|---|---|---|---|---|
| Essential: `cli_helpListsSupportedCommands` | Start interactive CLI with captured input/output. | Enter `help`, then `exit`. | Help shows exact command forms; session ends with `Goodbye.`; no business state changes. | Approved CLI contract; I6. |
| Essential: `cli_statusDisplaysAggregateCounts` | Start with capacity 3, empty state. | Enter `status`, then `exit`. | Displays capacity 3, enrolled 0, available 3, waitlisted 0; state is unchanged. | Requested LLD status contract; I1, I5. |
| Essential: `cli_sampleSessionEnrollsWaitlistsDropsAndPromotes` | Fixture has four eligible IDs; capacity 3. | Run the documented sequence: enroll A/B/C, enroll D, drop B, status, exit. | Outputs three enrollments, D at waitlist position 1, then successful drop with D promoted; final counts are 3 enrolled, 0 available, 0 waitlisted. | FR1-FR3; I1-I5. |
| Essential: `cli_malformedInputReportsErrorAndContinues` | Use a spy service and scripted session input. | Send blank line, unknown command, missing ID, extra argument, then `help` and `exit`. | Each malformed line shows `INVALID_REQUEST`; none calls business methods; `help` still runs and session continues. | FR5; approved CLI behavior; I6. |
| Essential: `cli_malformedLearnerIdIsRejectedBeforeDispatch` | Spy service; input contains missing ID, whitespace-containing ID, and extra tokens. | Enter malformed `enroll` and `drop` commands. | Clear validation error; no service call or state change; session remains active. Direct service validation is covered separately. | Approved CLI/service validation; I5-I6. |
| Essential: `cli_rendersUnknownIneligibleAndInternalErrorsWithoutDetails` | Fixture has a known ineligible learner; another valid ID is unknown; inject prerequisite evaluation failure for a third. | Submit enroll commands for each. | Displays distinct `UNKNOWN_LEARNER`, `INELIGIBLE`, and generic `INTERNAL_ERROR`; no exception/fixture details leak. State changes only for none of these. | Approved CLI result contract; I3, I5-I6. |
| Secondary: `cli_outputFailureAfterCommitIsNotReportedAsRollback` | Use an output sink that fails after an enrollment result has been committed; track service call count. | Enroll an eligible learner and make result writing fail. | CLI does not retry and does not label the mutation as uncommitted `INTERNAL_ERROR`; committed state remains. If the error channel works, it reports `OUTPUT_ERROR`. This test does not block the first working implementation. | Approved atomic publication/output contract; I5. |
| Optional: `cli_outputFailureAfterNoChangeDoesNotClaimCommit` | Use a no-change result (duplicate enrollment or status) and failing output sink. | Execute command and fail result write. | Any `OUTPUT_ERROR` says no state change was made; state is unchanged. | Approved output contract; I5-I6. |

## Shared State Assertions

Run these assertions after every successful mutation and after every no-change/error case where applicable:

- `enrolledCount <= capacity`, and capacity remains positive (**I1**).
- Enrolled IDs are unique; waitlist IDs are unique; the two membership sets are disjoint (**I2**).
- Each newly enrolled, newly waitlisted, or promoted learner had eligibility `true` at that operation (**I3**).
- Compare the complete expected waitlist sequence, not only its size; skipped entries retain their relative order and new entries append at the tail (**I4**).
- For failed, invalid, unknown, ineligible, and duplicate cases, compare the complete snapshot before and after (**I5-I6**).
- Following failures, issue a separate operation and verify it completes, demonstrating the lock was released (**I7**).
- For `status`, verify the returned counts agree with the snapshot and that the snapshot itself is unchanged.

## Recommended Implementation Checklist

1. Select the Java build tool and configure JUnit 5; create the immutable `CourseOfferingState`, eligibility fixture, and in-memory store initialized to capacity 3. Run initialization and invalid-capacity checks.
2. Add result types and CLI/service learner-ID validation. Implement enrollment membership lookup, unknown/ineligible outcomes, available-capacity enrollment, full-capacity waitlisting, and duplicates. Run the focused enrollment tests.
3. Implement candidate snapshot creation and single-reference publication, then add drop, FIFO eligibility scan, skip-but-retain, and no-eligible open-seat behavior under the offering lock. Run drop, promotion, invariant, and failure-injection tests.
4. Add aggregate read-only status retrieval. Run status and state-consistency checks.
5. Implement the thin interactive CLI command parser, help/exit behavior, exact output mapping, and continue-after-malformed-input behavior. Run CLI session tests.
6. Run the essential concurrency tests and complete essential JUnit 5 suite using the selected build tool. After the first working implementation, run the secondary CLI output-failure case; it is not a release gate for the initial slice. Review failures and verify no unplanned infrastructure or production abstractions were added.

## Optional Hardening

- Property-based/randomized command sequences that assert I1-I7 after each operation.
- Repeated concurrent stress runs beyond the deterministic two-request race tests.
- An auxiliary waitlist index for O(1) position lookup. This is not recommended here because it duplicates state and adds consistency obligations.

## Blockers

No requirement or architecture blocker was found. The CLI status view is requested in the LLD but is not part of the assignment's functional acceptance criteria; it remains a read-only convenience and does not affect enrollment or promotion behavior.
