package com.courseenrollment.store;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class EligibilityFixture {
    private final Map<String, Boolean> eligibilityByLearnerId;

    public EligibilityFixture() {
        this(Map.of());
    }

    public EligibilityFixture(Map<String, Boolean> initialEligibility) {
        Objects.requireNonNull(initialEligibility, "initialEligibility");
        this.eligibilityByLearnerId = new HashMap<>();
        initialEligibility.forEach((learnerId, eligible) -> {
            this.eligibilityByLearnerId.put(
                    Objects.requireNonNull(learnerId, "learnerId"),
                    Objects.requireNonNull(eligible, "eligible"));
        });
    }

    public Optional<Boolean> lookupEligibility(String learnerId) {
        return Optional.ofNullable(eligibilityByLearnerId.get(learnerId));
    }

    public void setEligibility(String learnerId, boolean eligible) {
        Objects.requireNonNull(learnerId, "learnerId");
        if (!eligibilityByLearnerId.containsKey(learnerId)) {
            throw new IllegalArgumentException("Learner is not present in the fixture.");
        }
        eligibilityByLearnerId.put(learnerId, eligible);
    }
}
