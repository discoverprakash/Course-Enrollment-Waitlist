package com.courseenrollment.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CourseOfferingState {
    private final int capacity;
    private final Set<String> enrolledLearnerIds;
    private final List<String> waitlist;

    public CourseOfferingState(
            int capacity,
            Set<String> enrolledLearnerIds,
            List<String> waitlist) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive.");
        }

        Objects.requireNonNull(enrolledLearnerIds, "enrolledLearnerIds");
        Objects.requireNonNull(waitlist, "waitlist");

        Set<String> enrolledCopy = Set.copyOf(enrolledLearnerIds);
        List<String> waitlistCopy = List.copyOf(waitlist);
        Set<String> uniqueWaitlistIds = new HashSet<>(waitlistCopy);

        if (enrolledCopy.size() > capacity) {
            throw new IllegalArgumentException("Enrollment cannot exceed capacity.");
        }
        if (uniqueWaitlistIds.size() != waitlistCopy.size()) {
            throw new IllegalArgumentException("Waitlist cannot contain duplicate learners.");
        }
        if (!java.util.Collections.disjoint(enrolledCopy, uniqueWaitlistIds)) {
            throw new IllegalArgumentException("A learner cannot be enrolled and waitlisted.");
        }

        this.capacity = capacity;
        this.enrolledLearnerIds = enrolledCopy;
        this.waitlist = waitlistCopy;
    }

    public static CourseOfferingState empty(int capacity) {
        return new CourseOfferingState(capacity, Set.of(), List.of());
    }

    public int getCapacity() {
        return capacity;
    }

    public Set<String> getEnrolledLearnerIds() {
        return enrolledLearnerIds;
    }

    public List<String> getWaitlist() {
        return waitlist;
    }
}
