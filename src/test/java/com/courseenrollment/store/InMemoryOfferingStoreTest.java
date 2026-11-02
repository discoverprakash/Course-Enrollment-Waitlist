package com.courseenrollment.store;

import com.courseenrollment.domain.CourseOfferingState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryOfferingStoreTest {
    @Test
    void defaultStoreInitializesDemoOfferingAtCapacityThree() {
        InMemoryOfferingStore store = new InMemoryOfferingStore();
        CourseOfferingState state = store.readState();

        assertEquals(3, state.getCapacity());
        assertTrue(state.getEnrolledLearnerIds().isEmpty());
        assertTrue(state.getWaitlist().isEmpty());
    }

    @Test
    void constructorAllowsOtherPositiveCapacities() {
        InMemoryOfferingStore store = new InMemoryOfferingStore(5);

        assertEquals(5, store.readState().getCapacity());
        assertTrue(store.readState().getEnrolledLearnerIds().isEmpty());
        assertTrue(store.readState().getWaitlist().isEmpty());
    }

    @Test
    void readStateReturnsPublishedSnapshotAndPublishReplacesIt() {
        InMemoryOfferingStore store = new InMemoryOfferingStore();
        CourseOfferingState initial = store.readState();
        CourseOfferingState next = new CourseOfferingState(
            3,
                Set.of("learner-a"),
                List.of("learner-b"));

        store.publishState(next);

        assertSame(next, store.readState());
        assertEquals(3, initial.getCapacity());
        assertTrue(initial.getEnrolledLearnerIds().isEmpty());
        assertTrue(initial.getWaitlist().isEmpty());
        assertEquals(3, store.readState().getCapacity());
        }

        @Test
        void publishStateRejectsCapacityChangeWithoutReplacingSnapshot() {
        InMemoryOfferingStore store = new InMemoryOfferingStore(3);
        CourseOfferingState initial = store.readState();
        CourseOfferingState differentCapacity = CourseOfferingState.empty(4);

        assertThrows(IllegalArgumentException.class,
            () -> store.publishState(differentCapacity));

        assertSame(initial, store.readState());
    }

    @Test
    void nullPublicationFailsBeforeReplacingCurrentSnapshot() {
        InMemoryOfferingStore store = new InMemoryOfferingStore();
        CourseOfferingState initial = store.readState();

        assertThrows(NullPointerException.class, () -> store.publishState(null));

        assertSame(initial, store.readState());
    }

    @Test
    void eligibilityFixtureDistinguishesUnknownAndIneligibleAndAllowsUpdates() {
        EligibilityFixture fixture = new EligibilityFixture(Map.of(
                "learner-a", true,
                "learner-b", false));
        InMemoryOfferingStore store = new InMemoryOfferingStore(3, fixture);

        assertEquals(java.util.Optional.of(true), store.lookupEligibility("learner-a"));
        assertEquals(java.util.Optional.of(false), store.lookupEligibility("learner-b"));
        assertTrue(store.lookupEligibility("learner-unknown").isEmpty());

        fixture.setEligibility("learner-a", false);
        assertFalse(store.lookupEligibility("learner-a").orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> fixture.setEligibility("learner-unknown", true));
    }
}
