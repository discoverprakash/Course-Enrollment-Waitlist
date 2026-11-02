package com.courseenrollment.service;

import java.util.Objects;
import java.util.Optional;

public record DropResult(Status status, Optional<String> promotedLearnerId) {
    public enum Status {
        DROPPED,
        NOT_ENROLLED,
        UNKNOWN_LEARNER,
        INVALID_REQUEST,
        INTERNAL_ERROR
    }

    public DropResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(promotedLearnerId, "promotedLearnerId");
        if (status != Status.DROPPED && promotedLearnerId.isPresent()) {
            throw new IllegalArgumentException("Only a successful drop can report a promoted learner.");
        }
    }

    public static DropResult of(Status status) {
        return new DropResult(status, Optional.empty());
    }

    public static DropResult dropped(Optional<String> promotedLearnerId) {
        return new DropResult(Status.DROPPED, promotedLearnerId);
    }
}
