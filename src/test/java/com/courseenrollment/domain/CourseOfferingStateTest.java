package com.courseenrollment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CourseOfferingStateTest {
    @Test
    void emptyStateStartsWithConfiguredCapacityAndNoMembership() {
        CourseOfferingState state = CourseOfferingState.empty(3);

        assertEquals(3, state.getCapacity());
        assertEquals(Set.of(), state.getEnrolledLearnerIds());
        assertEquals(List.of(), state.getWaitlist());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveCapacity(int capacity) {
        assertThrows(IllegalArgumentException.class, () -> CourseOfferingState.empty(capacity));
    }

    @Test
    void copiesInputsAndExposesUnmodifiableCollections() {
        Set<String> enrolledInput = new HashSet<>(Set.of("learner-a"));
        List<String> waitlistInput = new ArrayList<>(List.of("learner-b", "learner-c"));
        CourseOfferingState state = new CourseOfferingState(3, enrolledInput, waitlistInput);

        enrolledInput.add("learner-d");
        waitlistInput.add("learner-d");

        assertEquals(Set.of("learner-a"), state.getEnrolledLearnerIds());
        assertEquals(List.of("learner-b", "learner-c"), state.getWaitlist());
        assertThrows(UnsupportedOperationException.class,
                () -> state.getEnrolledLearnerIds().add("learner-e"));
        assertThrows(UnsupportedOperationException.class,
                () -> state.getWaitlist().add("learner-e"));
    }

    @Test
    void rejectsEnrollmentAboveCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOfferingState(1, Set.of("learner-a", "learner-b"), List.of()));
    }

    @Test
    void rejectsDuplicateOrOverlappingActiveMembership() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOfferingState(3, Set.of("learner-a"), List.of("learner-a")));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOfferingState(3, Set.of(), List.of("learner-b", "learner-b")));
    }
}
