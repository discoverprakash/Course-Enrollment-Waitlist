package com.courseenrollment.domain;

public record OfferingStatus(int capacity, int enrolledCount, int availableSeats, int waitlistCount) {
    public OfferingStatus {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive.");
        }
        if (enrolledCount < 0 || enrolledCount > capacity) {
            throw new IllegalArgumentException("Enrolled count must be between zero and capacity.");
        }
        if (availableSeats != capacity - enrolledCount) {
            throw new IllegalArgumentException("Available seats must match capacity minus enrollment.");
        }
        if (waitlistCount < 0) {
            throw new IllegalArgumentException("Waitlist count cannot be negative.");
        }
    }
}
