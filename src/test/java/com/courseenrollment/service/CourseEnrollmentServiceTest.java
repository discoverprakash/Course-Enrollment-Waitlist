package com.courseenrollment.service;

import com.courseenrollment.domain.CourseOfferingState;
import com.courseenrollment.store.EligibilityFixture;
import com.courseenrollment.store.InMemoryOfferingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.courseenrollment.service.EnrollmentResult.Status.ALREADY_ENROLLED;
import static com.courseenrollment.service.EnrollmentResult.Status.ALREADY_WAITLISTED;
import static com.courseenrollment.service.EnrollmentResult.Status.ENROLLED;
import static com.courseenrollment.service.EnrollmentResult.Status.INELIGIBLE;
import static com.courseenrollment.service.EnrollmentResult.Status.INTERNAL_ERROR;
import static com.courseenrollment.service.EnrollmentResult.Status.INVALID_REQUEST;
import static com.courseenrollment.service.EnrollmentResult.Status.UNKNOWN_LEARNER;
import static com.courseenrollment.service.EnrollmentResult.Status.WAITLISTED;
import static com.courseenrollment.service.DropResult.Status.DROPPED;
import static com.courseenrollment.service.DropResult.Status.NOT_ENROLLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseEnrollmentServiceTest {
    @Test
    void enrollEligibleLearnerWithAvailableSeatEnrolls() {
        InMemoryOfferingStore store = store(2, Map.of("learner-a", true));
        CourseOfferingState initialState = store.readState();
        CourseEnrollmentService service = new CourseEnrollmentService(store);

        EnrollmentResult result = service.enroll("learner-a");

        assertEquals(ENROLLED, result.status());
        assertTrue(result.waitlistPosition().isEmpty());
        assertEquals(Set.of("learner-a"), store.readState().getEnrolledLearnerIds());
        assertTrue(store.readState().getWaitlist().isEmpty());
        assertEquals(1, store.readState().getCapacity() - store.readState().getEnrolledLearnerIds().size());
        assertFalse(initialState == store.readState());
    }

    @Test
    void enrollWhenFullAppendsToFifoWaitlistAndReturnsOneBasedPosition() {
        InMemoryOfferingStore store = store(1, Map.of(
                "owner", true,
                "waiter-a", true,
                "learner-b", true));
        store.publishState(new CourseOfferingState(
                1, Set.of("owner"), List.of("waiter-a")));
        CourseEnrollmentService service = new CourseEnrollmentService(store);

        EnrollmentResult result = service.enroll("learner-b");

        assertEquals(WAITLISTED, result.status());
        assertEquals(2, result.waitlistPosition().orElseThrow());
        assertEquals(Set.of("owner"), store.readState().getEnrolledLearnerIds());
        assertEquals(List.of("waiter-a", "learner-b"), store.readState().getWaitlist());
        assertEquals(1, store.readState().getEnrolledLearnerIds().size());
    }

    @Test
    void duplicateEnrolledRequestReturnsExistingStateAfterEligibilityChanges() {
        EligibilityFixture fixture = new EligibilityFixture(Map.of("learner-a", true));
        InMemoryOfferingStore store = new InMemoryOfferingStore(1, fixture);
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        assertEquals(ENROLLED, service.enroll("learner-a").status());
        CourseOfferingState stateBeforeDuplicate = store.readState();
        fixture.setEligibility("learner-a", false);

        EnrollmentResult result = service.enroll("learner-a");

        assertEquals(ALREADY_ENROLLED, result.status());
        assertTrue(result.waitlistPosition().isEmpty());
        assertSame(stateBeforeDuplicate, store.readState());
    }

    @Test
    void duplicateWaitlistedRequestReturnsPositionWithoutRecheckingEligibility() {
        EligibilityFixture fixture = new EligibilityFixture(Map.of(
                "owner", true,
                "learner-a", true,
                "learner-b", true));
        InMemoryOfferingStore store = new InMemoryOfferingStore(1, fixture);
        store.publishState(new CourseOfferingState(
                1, Set.of("owner"), List.of("learner-a", "learner-b")));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        CourseOfferingState stateBeforeDuplicate = store.readState();
        fixture.setEligibility("learner-b", false);

        EnrollmentResult result = service.enroll("learner-b");

        assertEquals(ALREADY_WAITLISTED, result.status());
        assertEquals(2, result.waitlistPosition().orElseThrow());
        assertSame(stateBeforeDuplicate, store.readState());
    }

    @Test
    void duplicateWaitlistedRequestUsesCurrentPositionAfterEarlierPromotion() {
        InMemoryOfferingStore store = store(1, Map.of("owner", true, "learner-b", true));
        store.publishState(new CourseOfferingState(1, Set.of("owner"), List.of("learner-b")));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        CourseOfferingState stateBeforeDuplicate = store.readState();

        EnrollmentResult result = service.enroll("learner-b");

        assertEquals(ALREADY_WAITLISTED, result.status());
        assertEquals(1, result.waitlistPosition().orElseThrow());
        assertEquals(List.of("learner-b"), store.readState().getWaitlist());
        assertSame(stateBeforeDuplicate, store.readState());
    }

    @Test
    void unknownAndKnownIneligibleLearnersHaveDistinctOutcomes() {
        InMemoryOfferingStore store = store(1, Map.of("learner-ineligible", false));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        CourseOfferingState stateBeforeRequests = store.readState();

        EnrollmentResult unknown = service.enroll("learner-unknown");
        EnrollmentResult ineligible = service.enroll("learner-ineligible");

        assertEquals(UNKNOWN_LEARNER, unknown.status());
        assertEquals(INELIGIBLE, ineligible.status());
        assertSame(stateBeforeRequests, store.readState());
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"   ", "learner id", "learner\tid"})
    void malformedLearnerIdsReturnInvalidRequestWithoutChangingState(String learnerId) {
        InMemoryOfferingStore store = store(2, Map.of("learner-a", true));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        CourseOfferingState stateBeforeRequest = store.readState();

        EnrollmentResult result = service.enroll(learnerId);

        assertEquals(INVALID_REQUEST, result.status());
        assertTrue(result.waitlistPosition().isEmpty());
        assertSame(stateBeforeRequest, store.readState());
    }

    @Test
    void concurrentDistinctEnrollmentsForLastSeatAllowExactlyOneEnrollment() throws Exception {
        InMemoryOfferingStore store = store(1, Map.of("learner-a", true, "learner-b", true));
        CourseEnrollmentService service = new CourseEnrollmentService(store);

        List<EnrollmentResult> results = invokeConcurrently(service, "learner-a", "learner-b");

        assertEquals(Set.of(ENROLLED, WAITLISTED), statuses(results));
        assertEquals(1, store.readState().getEnrolledLearnerIds().size());
        assertEquals(1, store.readState().getWaitlist().size());
        assertTrue(java.util.Collections.disjoint(
                store.readState().getEnrolledLearnerIds(), store.readState().getWaitlist()));
        assertEquals(1, store.readState().getCapacity());
    }

    @Test
    void concurrentDuplicateEnrollmentCreatesOneMembershipWhenAvailableOrFull() throws Exception {
        InMemoryOfferingStore availableStore = store(1, Map.of("learner-a", true));
        List<EnrollmentResult> availableResults = invokeConcurrently(
                new CourseEnrollmentService(availableStore), "learner-a", "learner-a");

        assertEquals(Set.of(ENROLLED, ALREADY_ENROLLED), statuses(availableResults));
        assertEquals(Set.of("learner-a"), availableStore.readState().getEnrolledLearnerIds());
        assertTrue(availableStore.readState().getWaitlist().isEmpty());

        InMemoryOfferingStore fullStore = store(1, Map.of("owner", true, "learner-a", true));
        fullStore.publishState(new CourseOfferingState(1, Set.of("owner"), List.of()));
        List<EnrollmentResult> fullResults = invokeConcurrently(
                new CourseEnrollmentService(fullStore), "learner-a", "learner-a");

        assertEquals(Set.of(WAITLISTED, ALREADY_WAITLISTED), statuses(fullResults));
        assertEquals(1, fullResults.stream()
                .filter(result -> result.waitlistPosition().isPresent())
                .mapToInt(result -> result.waitlistPosition().orElseThrow())
                .distinct()
                .count());
        assertEquals(List.of("learner-a"), fullStore.readState().getWaitlist());
        assertEquals(Set.of("owner"), fullStore.readState().getEnrolledLearnerIds());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void prerequisiteEvaluationFailurePreservesStateAndRecovers(boolean offeringFull) throws Exception {
        FailingEligibilityFixture fixture = new FailingEligibilityFixture(
                Map.of("learner-a", true, "owner", true));
        InMemoryOfferingStore store = new InMemoryOfferingStore(1, fixture);
        if (offeringFull) {
            store.publishState(new CourseOfferingState(1, Set.of("owner"), List.of()));
        }
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        CourseOfferingState stateBeforeFailure = store.readState();

        EnrollmentResult failure = service.enroll("learner-a");

        assertEquals(INTERNAL_ERROR, failure.status());
        assertTrue(failure.waitlistPosition().isEmpty());
        assertSame(stateBeforeFailure, store.readState());

        fixture.stopFailing();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<EnrollmentResult> recovery = executor.submit(() -> service.enroll("learner-a"));
            EnrollmentResult recovered = recovery.get(5, TimeUnit.SECONDS);

            assertEquals(offeringFull ? WAITLISTED : ENROLLED, recovered.status());
            assertEquals(offeringFull ? List.of("learner-a") : List.of(), store.readState().getWaitlist());
            assertEquals(offeringFull ? Set.of("owner") : Set.of("learner-a"),
                    store.readState().getEnrolledLearnerIds());
            assertTrue(store.readState().getEnrolledLearnerIds().size() <= store.readState().getCapacity());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void dropWithoutWaitlistRemovesEnrollmentAndLeavesSeatOpen() {
        InMemoryOfferingStore store = store(2, Map.of("owner", true));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        assertEquals(ENROLLED, service.enroll("owner").status());
        CourseOfferingState stateBeforeDrop = store.readState();

        DropResult result = service.drop("owner");

        assertEquals(DROPPED, result.status());
        assertTrue(result.promotedLearnerId().isEmpty());
        assertFalse(stateBeforeDrop == store.readState());
        assertTrue(store.readState().getEnrolledLearnerIds().isEmpty());
        assertTrue(store.readState().getWaitlist().isEmpty());
        assertMembershipInvariants(store);
    }

    @Test
    void dropUnknownAndKnownNotEnrolledLearnersDoesNotChangeState() {
        InMemoryOfferingStore store = store(2, Map.of("known-learner", true));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        CourseOfferingState stateBeforeRequests = store.readState();

        DropResult unknown = service.drop("unknown-learner");
        DropResult notEnrolled = service.drop("known-learner");

        assertEquals(DropResult.Status.UNKNOWN_LEARNER, unknown.status());
        assertEquals(NOT_ENROLLED, notEnrolled.status());
        assertSame(stateBeforeRequests, store.readState());
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"   ", "learner id", "learner\tid"})
    void dropMalformedLearnerIdsReturnInvalidRequestWithoutChangingState(String learnerId) {
        InMemoryOfferingStore store = store(2, Map.of("learner-a", true));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        CourseOfferingState stateBeforeRequest = store.readState();

        DropResult result = service.drop(learnerId);

        assertEquals(DropResult.Status.INVALID_REQUEST, result.status());
        assertTrue(result.promotedLearnerId().isEmpty());
        assertSame(stateBeforeRequest, store.readState());
    }

    @Test
    void droppingWaitlistedLearnerReturnsNotEnrolledAndRetainsQueueEntry() {
        InMemoryOfferingStore store = store(1, Map.of("owner", true, "waiter", true));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        fillOfferingAndWaitlist(service, "owner", "waiter");
        CourseOfferingState stateBeforeDrop = store.readState();

        DropResult result = service.drop("waiter");

        assertEquals(NOT_ENROLLED, result.status());
        assertSame(stateBeforeDrop, store.readState());
        assertEquals(Set.of("owner"), store.readState().getEnrolledLearnerIds());
        assertEquals(List.of("waiter"), store.readState().getWaitlist());
    }

    @Test
    void dropPromotesFirstEligibleWaiterInFifoOrder() {
        InMemoryOfferingStore store = store(1, Map.of(
                "owner", true,
                "waiter-a", true,
                "waiter-b", true,
                "waiter-c", true));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        fillOfferingAndWaitlist(service, "owner", "waiter-a", "waiter-b", "waiter-c");

        DropResult result = service.drop("owner");

        assertEquals(DROPPED, result.status());
        assertEquals(Optional.of("waiter-a"), result.promotedLearnerId());
        assertEquals(Set.of("waiter-a"), store.readState().getEnrolledLearnerIds());
        assertEquals(List.of("waiter-b", "waiter-c"), store.readState().getWaitlist());
        assertMembershipInvariants(store);
    }

    @Test
    void dropSkipsAndRetainsIneligibleWaiterWhilePromotingNextEligible() {
        EligibilityFixture fixture = new EligibilityFixture(Map.of(
                "owner", true,
                "waiter-a", true,
                "waiter-b", true,
                "waiter-c", true));
        InMemoryOfferingStore store = new InMemoryOfferingStore(1, fixture);
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        fillOfferingAndWaitlist(service, "owner", "waiter-a", "waiter-b", "waiter-c");
        fixture.setEligibility("waiter-a", false);

        DropResult result = service.drop("owner");

        assertEquals(DROPPED, result.status());
        assertEquals(Optional.of("waiter-b"), result.promotedLearnerId());
        assertEquals(Set.of("waiter-b"), store.readState().getEnrolledLearnerIds());
        assertEquals(List.of("waiter-a", "waiter-c"), store.readState().getWaitlist());
        assertMembershipInvariants(store);
    }

    @Test
    void dropWithAllWaitersIneligibleLeavesSeatAvailable() {
        EligibilityFixture fixture = new EligibilityFixture(Map.of(
                "owner", true,
                "waiter-a", true,
                "waiter-b", true));
        InMemoryOfferingStore store = new InMemoryOfferingStore(1, fixture);
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        fillOfferingAndWaitlist(service, "owner", "waiter-a", "waiter-b");
        fixture.setEligibility("waiter-a", false);
        fixture.setEligibility("waiter-b", false);

        DropResult result = service.drop("owner");

        assertEquals(DROPPED, result.status());
        assertTrue(result.promotedLearnerId().isEmpty());
        assertTrue(store.readState().getEnrolledLearnerIds().isEmpty());
        assertEquals(1, store.readState().getCapacity());
        assertEquals(List.of("waiter-a", "waiter-b"), store.readState().getWaitlist());
        assertMembershipInvariants(store);
    }

    @Test
    void dropEvaluationFailurePreservesSnapshotAndRecoversFromAnotherThread() throws Exception {
        FailingEligibilityFixture fixture = new FailingEligibilityFixture(Map.of(
                "owner", true,
                "waiter-a", true,
                "waiter-b", true,
                "waiter-c", true));
        fixture.stopFailing();
        InMemoryOfferingStore store = new InMemoryOfferingStore(1, fixture);
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        fillOfferingAndWaitlist(service, "owner", "waiter-a", "waiter-b", "waiter-c");
        fixture.setEligibility("waiter-a", false);
        fixture.failFor("waiter-b");
        CourseOfferingState stateBeforeFailure = store.readState();

        DropResult failure = service.drop("owner");

        assertEquals(DropResult.Status.INTERNAL_ERROR, failure.status());
        assertTrue(failure.promotedLearnerId().isEmpty());
        assertSame(stateBeforeFailure, store.readState());

        fixture.stopFailing();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<DropResult> recovery = executor.submit(() -> service.drop("owner"));
            DropResult recovered = recovery.get(5, TimeUnit.SECONDS);

            assertEquals(DROPPED, recovered.status());
            assertEquals(Optional.of("waiter-b"), recovered.promotedLearnerId());
            assertEquals(Set.of("waiter-b"), store.readState().getEnrolledLearnerIds());
            assertEquals(List.of("waiter-a", "waiter-c"), store.readState().getWaitlist());
            assertMembershipInvariants(store);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void duplicateWaitlistRequestGetsCurrentPositionAfterEarlierWaiterIsPromoted() {
        InMemoryOfferingStore store = store(1, Map.of(
                "owner", true,
                "waiter-a", true,
                "waiter-b", true));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        fillOfferingAndWaitlist(service, "owner", "waiter-a", "waiter-b");

        DropResult dropResult = service.drop("owner");
        EnrollmentResult duplicate = service.enroll("waiter-b");

        assertEquals(Optional.of("waiter-a"), dropResult.promotedLearnerId());
        assertEquals(ALREADY_WAITLISTED, duplicate.status());
        assertEquals(1, duplicate.waitlistPosition().orElseThrow());
        assertEquals(List.of("waiter-b"), store.readState().getWaitlist());
        assertMembershipInvariants(store);
    }

    @Test
    void newEnrollmentFillsOpenSeatWithoutRescanningRetainedWaitlist() {
        EligibilityFixture fixture = new EligibilityFixture(Map.of(
                "owner", true,
                "waiter-a", true,
                "learner-b", true));
        InMemoryOfferingStore store = new InMemoryOfferingStore(1, fixture);
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        fillOfferingAndWaitlist(service, "owner", "waiter-a");
        fixture.setEligibility("waiter-a", false);

        DropResult dropResult = service.drop("owner");
        fixture.setEligibility("waiter-a", true);
        EnrollmentResult enrollResult = service.enroll("learner-b");

        assertEquals(DROPPED, dropResult.status());
        assertTrue(dropResult.promotedLearnerId().isEmpty());
        assertEquals(ENROLLED, enrollResult.status());
        assertEquals(Set.of("learner-b"), store.readState().getEnrolledLearnerIds());
        assertEquals(List.of("waiter-a"), store.readState().getWaitlist());
        assertMembershipInvariants(store);
    }

    @Test
    void concurrentEnrollmentAndDropPreserveCapacityAndMembership() throws Exception {
        InMemoryOfferingStore store = store(1, Map.of("owner", true, "learner-a", true));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        assertEquals(ENROLLED, service.enroll("owner").status());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<EnrollmentResult> enroll = executor.submit(() -> {
                awaitConcurrentStart(ready, start);
                return service.enroll("learner-a");
            });
            Future<DropResult> drop = executor.submit(() -> {
                awaitConcurrentStart(ready, start);
                return service.drop("owner");
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            EnrollmentResult enrollResult = enroll.get(5, TimeUnit.SECONDS);
            DropResult dropResult = drop.get(5, TimeUnit.SECONDS);

            assertTrue(enrollResult.status() == ENROLLED || enrollResult.status() == WAITLISTED);
            assertEquals(DROPPED, dropResult.status());
            if (enrollResult.status() == WAITLISTED) {
                assertEquals(Optional.of("learner-a"), dropResult.promotedLearnerId());
            } else {
                assertTrue(dropResult.promotedLearnerId().isEmpty());
            }
            assertEquals(Set.of("learner-a"), store.readState().getEnrolledLearnerIds());
            assertTrue(store.readState().getWaitlist().isEmpty());
            assertMembershipInvariants(store);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static void fillOfferingAndWaitlist(
            CourseEnrollmentService service,
            String owner,
            String... waitlistedLearners) {
        assertEquals(ENROLLED, service.enroll(owner).status());
        for (int index = 0; index < waitlistedLearners.length; index++) {
            EnrollmentResult result = service.enroll(waitlistedLearners[index]);
            assertEquals(WAITLISTED, result.status());
            assertEquals(index + 1, result.waitlistPosition().orElseThrow());
        }
    }

    private static void assertMembershipInvariants(InMemoryOfferingStore store) {
        CourseOfferingState state = store.readState();
        assertTrue(state.getEnrolledLearnerIds().size() <= state.getCapacity());
        assertEquals(state.getWaitlist().size(), new HashSet<>(state.getWaitlist()).size());
        assertTrue(java.util.Collections.disjoint(
                state.getEnrolledLearnerIds(), state.getWaitlist()));
    }

    private static void awaitConcurrentStart(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent start.");
        }
    }

    private static InMemoryOfferingStore store(int capacity, Map<String, Boolean> eligibility) {
        return new InMemoryOfferingStore(capacity, new EligibilityFixture(eligibility));
    }

    private static Set<EnrollmentResult.Status> statuses(List<EnrollmentResult> results) {
        return results.stream()
                .map(EnrollmentResult::status)
                .collect(Collectors.toSet());
    }

    private static List<EnrollmentResult> invokeConcurrently(
            CourseEnrollmentService service,
            String firstLearnerId,
            String secondLearnerId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<EnrollmentResult> first = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent start.");
                }
                return service.enroll(firstLearnerId);
            });
            Future<EnrollmentResult> second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent start.");
                }
                return service.enroll(secondLearnerId);
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static final class FailingEligibilityFixture extends EligibilityFixture {
        private final AtomicBoolean shouldFail = new AtomicBoolean(true);
        private final AtomicReference<String> failingLearnerId = new AtomicReference<>("*");

        private FailingEligibilityFixture(Map<String, Boolean> initialEligibility) {
            super(initialEligibility);
        }

        @Override
        public Optional<Boolean> lookupEligibility(String learnerId) {
            if (shouldFail.get()
                    && ("*".equals(failingLearnerId.get())
                    || learnerId.equals(failingLearnerId.get()))) {
                throw new IllegalStateException("Injected prerequisite evaluation failure.");
            }
            return super.lookupEligibility(learnerId);
        }

        private void failFor(String learnerId) {
            failingLearnerId.set(learnerId);
            shouldFail.set(true);
        }

        private void stopFailing() {
            shouldFail.set(false);
        }
    }
}
