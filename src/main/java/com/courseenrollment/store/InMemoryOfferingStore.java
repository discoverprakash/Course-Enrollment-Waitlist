package com.courseenrollment.store;

import com.courseenrollment.domain.CourseOfferingState;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryOfferingStore {
    private static final int DEMO_CAPACITY = 3;

    private final AtomicReference<CourseOfferingState> state;
    private final EligibilityFixture eligibilityFixture;

    public InMemoryOfferingStore() {
        this(DEMO_CAPACITY, new EligibilityFixture());
    }

    public InMemoryOfferingStore(int capacity) {
        this(capacity, new EligibilityFixture());
    }

    public InMemoryOfferingStore(int capacity, EligibilityFixture eligibilityFixture) {
        this.state = new AtomicReference<>(CourseOfferingState.empty(capacity));
        this.eligibilityFixture = Objects.requireNonNull(eligibilityFixture, "eligibilityFixture");
    }

    public CourseOfferingState readState() {
        return state.get();
    }

    public void publishState(CourseOfferingState nextState) {
        Objects.requireNonNull(nextState, "nextState");
        state.updateAndGet(currentState -> {
            if (nextState.getCapacity() != currentState.getCapacity()) {
                throw new IllegalArgumentException("Offering capacity cannot change after initialization.");
            }
            return nextState;
        });
    }

    public Optional<Boolean> lookupEligibility(String learnerId) {
        return eligibilityFixture.lookupEligibility(learnerId);
    }
}
