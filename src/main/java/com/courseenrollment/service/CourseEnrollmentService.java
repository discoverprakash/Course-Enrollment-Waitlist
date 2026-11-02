package com.courseenrollment.service;

import com.courseenrollment.domain.CourseOfferingState;
import com.courseenrollment.domain.OfferingStatus;
import com.courseenrollment.store.InMemoryOfferingStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import static com.courseenrollment.service.EnrollmentResult.Status.ALREADY_ENROLLED;
import static com.courseenrollment.service.EnrollmentResult.Status.ALREADY_WAITLISTED;
import static com.courseenrollment.service.EnrollmentResult.Status.ENROLLED;
import static com.courseenrollment.service.EnrollmentResult.Status.INELIGIBLE;
import static com.courseenrollment.service.EnrollmentResult.Status.INTERNAL_ERROR;
import static com.courseenrollment.service.EnrollmentResult.Status.INVALID_REQUEST;
import static com.courseenrollment.service.EnrollmentResult.Status.UNKNOWN_LEARNER;
import static com.courseenrollment.service.EnrollmentResult.Status.WAITLISTED;
import static com.courseenrollment.service.DropResult.Status.NOT_ENROLLED;

public final class CourseEnrollmentService {
    private final InMemoryOfferingStore store;
    private final ReentrantLock offeringLock = new ReentrantLock();

    public CourseEnrollmentService(InMemoryOfferingStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public EnrollmentResult enroll(String learnerId) {
        if (isInvalidLearnerId(learnerId)) {
            return EnrollmentResult.of(INVALID_REQUEST);
        }

        offeringLock.lock();
        try {
            CourseOfferingState currentState = store.readState();

            if (currentState.getEnrolledLearnerIds().contains(learnerId)) {
                return EnrollmentResult.of(ALREADY_ENROLLED);
            }

            int existingWaitlistIndex = currentState.getWaitlist().indexOf(learnerId);
            if (existingWaitlistIndex >= 0) {
                return EnrollmentResult.waitlisted(ALREADY_WAITLISTED, existingWaitlistIndex + 1);
            }

            Optional<Boolean> eligibility;
            try {
                eligibility = store.lookupEligibility(learnerId);
            } catch (RuntimeException evaluationFailure) {
                return EnrollmentResult.of(INTERNAL_ERROR);
            }
            if (eligibility == null) {
                return EnrollmentResult.of(INTERNAL_ERROR);
            }
            if (eligibility.isEmpty()) {
                return EnrollmentResult.of(UNKNOWN_LEARNER);
            }
            if (!eligibility.get()) {
                return EnrollmentResult.of(INELIGIBLE);
            }

            CourseOfferingState nextState;
            EnrollmentResult result;
            try {
                Set<String> enrolledLearnerIds = new HashSet<>(currentState.getEnrolledLearnerIds());
                List<String> waitlist = new ArrayList<>(currentState.getWaitlist());
                if (enrolledLearnerIds.size() < currentState.getCapacity()) {
                    enrolledLearnerIds.add(learnerId);
                    nextState = new CourseOfferingState(
                            currentState.getCapacity(), enrolledLearnerIds, waitlist);
                    result = EnrollmentResult.of(ENROLLED);
                } else {
                    waitlist.add(learnerId);
                    nextState = new CourseOfferingState(
                            currentState.getCapacity(), enrolledLearnerIds, waitlist);
                    result = EnrollmentResult.waitlisted(WAITLISTED, waitlist.size());
                }
            } catch (RuntimeException preparationFailure) {
                return EnrollmentResult.of(INTERNAL_ERROR);
            }

            try {
                store.publishState(nextState);
            } catch (RuntimeException publicationFailure) {
                return EnrollmentResult.of(INTERNAL_ERROR);
            }
            return result;
        } finally {
            offeringLock.unlock();
        }
    }

    public DropResult drop(String learnerId) {
        if (isInvalidLearnerId(learnerId)) {
            return DropResult.of(DropResult.Status.INVALID_REQUEST);
        }

        offeringLock.lock();
        try {
            CourseOfferingState currentState = store.readState();

            if (!currentState.getEnrolledLearnerIds().contains(learnerId)) {
                Optional<Boolean> eligibility;
                try {
                    eligibility = store.lookupEligibility(learnerId);
                } catch (RuntimeException evaluationFailure) {
                    return DropResult.of(DropResult.Status.INTERNAL_ERROR);
                }
                if (eligibility == null) {
                    return DropResult.of(DropResult.Status.INTERNAL_ERROR);
                }
                if (eligibility.isEmpty()) {
                    return DropResult.of(DropResult.Status.UNKNOWN_LEARNER);
                }
                return DropResult.of(NOT_ENROLLED);
            }

            Set<String> enrolledLearnerIds = new HashSet<>(currentState.getEnrolledLearnerIds());
            enrolledLearnerIds.remove(learnerId);
            List<String> waitlist = new ArrayList<>(currentState.getWaitlist());
            String promotedLearnerId = null;

            for (int index = 0; index < waitlist.size(); index++) {
                Optional<Boolean> eligibility;
                try {
                    eligibility = store.lookupEligibility(waitlist.get(index));
                } catch (RuntimeException evaluationFailure) {
                    return DropResult.of(DropResult.Status.INTERNAL_ERROR);
                }
                if (eligibility == null || eligibility.isEmpty()) {
                    return DropResult.of(DropResult.Status.INTERNAL_ERROR);
                }
                if (!eligibility.get()) {
                    continue;
                }

                promotedLearnerId = waitlist.remove(index);
                enrolledLearnerIds.add(promotedLearnerId);
                break;
            }

            CourseOfferingState nextState;
            DropResult result;
            try {
                nextState = new CourseOfferingState(
                        currentState.getCapacity(), enrolledLearnerIds, waitlist);
                result = DropResult.dropped(Optional.ofNullable(promotedLearnerId));
            } catch (RuntimeException preparationFailure) {
                return DropResult.of(DropResult.Status.INTERNAL_ERROR);
            }

            try {
                store.publishState(nextState);
            } catch (RuntimeException publicationFailure) {
                return DropResult.of(DropResult.Status.INTERNAL_ERROR);
            }
            return result;
        } finally {
            offeringLock.unlock();
        }
    }

    public OfferingStatus getStatus() {
        offeringLock.lock();
        try {
            CourseOfferingState currentState = store.readState();
            int enrolledCount = currentState.getEnrolledLearnerIds().size();
            return new OfferingStatus(
                    currentState.getCapacity(),
                    enrolledCount,
                    currentState.getCapacity() - enrolledCount,
                    currentState.getWaitlist().size());
        } finally {
            offeringLock.unlock();
        }
    }

    private static boolean isInvalidLearnerId(String learnerId) {
        return learnerId == null
                || learnerId.isBlank()
                || learnerId.chars().anyMatch(Character::isWhitespace);
    }
}
