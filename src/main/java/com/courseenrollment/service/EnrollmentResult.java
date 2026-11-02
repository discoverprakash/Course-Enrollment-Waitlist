package com.courseenrollment.service;

import java.util.Objects;
import java.util.OptionalInt;

public record EnrollmentResult(Status status, OptionalInt waitlistPosition) {
    public enum Status {
        ENROLLED,
        ALREADY_ENROLLED,
        WAITLISTED,
        ALREADY_WAITLISTED,
        INELIGIBLE,
        UNKNOWN_LEARNER,
        INVALID_REQUEST,
        INTERNAL_ERROR
    }

    public EnrollmentResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(waitlistPosition, "waitlistPosition");

        boolean waitlistOutcome = status == Status.WAITLISTED || status == Status.ALREADY_WAITLISTED;
        if (waitlistOutcome != waitlistPosition.isPresent()) {
            throw new IllegalArgumentException("Only waitlist outcomes have a position.");
        }
        if (waitlistPosition.isPresent() && waitlistPosition.getAsInt() < 1) {
            throw new IllegalArgumentException("Waitlist position must be one-based.");
        }
    }

    public static EnrollmentResult of(Status status) {
        return new EnrollmentResult(status, OptionalInt.empty());
    }

    public static EnrollmentResult waitlisted(Status status, int position) {
        if (status != Status.WAITLISTED && status != Status.ALREADY_WAITLISTED) {
            throw new IllegalArgumentException("Status must represent a waitlist outcome.");
        }
        return new EnrollmentResult(status, OptionalInt.of(position));
    }
}
