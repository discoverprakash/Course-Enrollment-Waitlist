package com.courseenrollment.cli;

import com.courseenrollment.domain.CourseOfferingState;
import com.courseenrollment.service.CourseEnrollmentService;
import com.courseenrollment.store.EligibilityFixture;
import com.courseenrollment.store.InMemoryOfferingStore;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.courseenrollment.service.EnrollmentResult.Status.ENROLLED;
import static com.courseenrollment.service.EnrollmentResult.Status.WAITLISTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseEnrollmentCliTest {
    @Test
    void helpListsCommandsAndExitEndsSession() {
        CliRun run = run(new InMemoryOfferingStore(), "help\nexit\n");

        assertTrue(run.output().contains("enroll <learnerId>  Enroll an eligible learner or join the waitlist."));
        assertTrue(run.output().contains("drop <learnerId>    Drop an enrollment and promote the first eligible waiter."));
        assertTrue(run.output().contains("status              Show capacity and enrollment/waitlist counts."));
        assertTrue(run.output().contains("Goodbye."));
    }

    @Test
    void endOfInputExitsCleanly() {
        CliRun run = run(new InMemoryOfferingStore(), "");

        assertTrue(run.output().contains("Goodbye."));
    }

    @Test
    void malformedCommandsReportErrorAndContinue() {
        CliRun run = run(new InMemoryOfferingStore(),
            "\nunknown\nenroll\ndrop learner-a extra\nstatus extra\nhelp extra\nexit extra\nhelp\nexit\n");

        assertEquals(7, occurrences(run.output(),
                "INVALID_REQUEST: Invalid command, arguments, or learner ID. Type help for command formats."));
        assertTrue(run.output().contains("enroll <learnerId>  Enroll an eligible learner or join the waitlist."));
        assertTrue(run.output().contains("Goodbye."));
    }

    @Test
    void invalidIdsUnknownLearnersAndIneligibleLearnersHaveExactMessages() {
        InMemoryOfferingStore store = store(2, Map.of("known-ineligible", false));
        CliRun run = run(store,
                "enroll bad id\nenroll unknown-learner\nenroll known-ineligible\nexit\n");

        assertTrue(run.output().contains(
                "INVALID_REQUEST: Invalid command, arguments, or learner ID. Type help for command formats."));
        assertTrue(run.output().contains(
                "UNKNOWN_LEARNER: unknown-learner is not in the learner eligibility fixture."));
        assertTrue(run.output().contains(
                "INELIGIBLE: known-ineligible does not satisfy the prerequisite."));
        assertTrue(store.readState().getEnrolledLearnerIds().isEmpty());
        assertTrue(store.readState().getWaitlist().isEmpty());
    }

    @Test
    void enrollAndWaitlistDisplayAssignedPosition() {
        InMemoryOfferingStore store = store(1, Map.of("learner-a", true, "learner-b", true));
        CliRun run = run(store, "enroll learner-a\nenroll learner-b\nexit\n");

        assertTrue(run.output().contains("ENROLLED: learner-a is enrolled."));
        assertTrue(run.output().contains("WAITLISTED: learner-b joined at position 1."));
        assertEquals(Set.of("learner-a"), store.readState().getEnrolledLearnerIds());
        assertEquals(List.of("learner-b"), store.readState().getWaitlist());
    }

    @Test
    void duplicateEnrollmentAndWaitlistDisplayCurrentStatuses() {
        InMemoryOfferingStore store = store(1, Map.of(
                "owner", true,
                "enrolled", true,
                "waitlisted", true));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        assertEquals(ENROLLED, service.enroll("owner").status());
        assertEquals(WAITLISTED, service.enroll("waitlisted").status());

        CliRun run = run(store, "enroll owner\nenroll waitlisted\nexit\n");

        assertTrue(run.output().contains("ALREADY_ENROLLED: owner is already enrolled."));
        assertTrue(run.output().contains("ALREADY_WAITLISTED: waitlisted is already waitlisted at position 1."));
    }

    @Test
    void dropDisplaysPromotionAndNoEligibleWaiterMessages() {
        EligibilityFixture fixture = new EligibilityFixture(Map.of(
                "owner", true,
                "waiter", true));
        InMemoryOfferingStore store = new InMemoryOfferingStore(1, fixture);
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        assertEquals(ENROLLED, service.enroll("owner").status());
        assertEquals(WAITLISTED, service.enroll("waiter").status());

        CliRun promoted = run(store, "drop owner\nexit\n");
        assertTrue(promoted.output().contains("DROPPED: owner dropped; waiter was promoted."));

        fixture.setEligibility("waiter", false);
        CliRun openSeat = run(store, "drop waiter\nexit\n");
        assertTrue(openSeat.output().contains(
                "DROPPED: waiter dropped; no eligible learner was available, so the seat is open."));
    }

    @Test
    void dropUnknownNotEnrolledAndWaitlistedLearnerMessagesAreCorrect() {
        InMemoryOfferingStore store = store(1, Map.of(
                "owner", true,
                "waiter", true,
                "known", true));
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        assertEquals(ENROLLED, service.enroll("owner").status());
        assertEquals(WAITLISTED, service.enroll("waiter").status());

        CliRun run = run(store, "drop missing\ndrop known\ndrop waiter\nexit\n");

        assertTrue(run.output().contains("UNKNOWN_LEARNER: missing is not in the learner eligibility fixture."));
        assertTrue(run.output().contains("NOT_ENROLLED: known has no active enrollment."));
        assertTrue(run.output().contains("NOT_ENROLLED: waiter has no active enrollment."));
        assertEquals(List.of("waiter"), store.readState().getWaitlist());
    }

    @Test
    void statusDisplaysAggregateCounts() {
        InMemoryOfferingStore store = store(3, Map.of("learner-a", true));
        assertEquals(ENROLLED, new CourseEnrollmentService(store).enroll("learner-a").status());

        CliRun run = run(store, "status\nexit\n");

        assertTrue(run.output().contains("Capacity: 3"));
        assertTrue(run.output().contains("Enrolled: 1"));
        assertTrue(run.output().contains("Available seats: 2"));
        assertTrue(run.output().contains("Waitlisted: 0"));
    }

    @Test
    void completeEnrollmentWaitlistDropPromotionWorkflowRunsThroughCli() {
        InMemoryOfferingStore store = store(3, Map.of(
                "learner-a", true,
                "learner-b", true,
                "learner-c", true,
                "learner-d", true));

        CliRun run = run(store,
                "enroll learner-a\nenroll learner-b\nenroll learner-c\n"
                        + "enroll learner-d\ndrop learner-b\nstatus\nexit\n");

        assertTrue(run.output().contains("ENROLLED: learner-a is enrolled."));
        assertTrue(run.output().contains("ENROLLED: learner-b is enrolled."));
        assertTrue(run.output().contains("ENROLLED: learner-c is enrolled."));
        assertTrue(run.output().contains("WAITLISTED: learner-d joined at position 1."));
        assertTrue(run.output().contains("DROPPED: learner-b dropped; learner-d was promoted."));
        assertTrue(run.output().contains("Enrolled: 3"));
        assertTrue(run.output().contains("Available seats: 0"));
        assertTrue(run.output().contains("Waitlisted: 0"));
        assertEquals(Set.of("learner-a", "learner-c", "learner-d"),
                store.readState().getEnrolledLearnerIds());
        assertTrue(store.readState().getWaitlist().isEmpty());
        assertFalse(store.readState().getEnrolledLearnerIds().contains("learner-b"));
    }

    @Test
    void internalErrorsUseGenericMessageWithoutTechnicalDetails() {
        ThrowingEligibilityFixture fixture = new ThrowingEligibilityFixture();
        InMemoryOfferingStore store = new InMemoryOfferingStore(2, fixture);
        CliRun run = run(store, "enroll learner-a\nexit\n");

        assertTrue(run.output().contains(
                "INTERNAL_ERROR: The operation failed before commit; no state was changed."));
        assertFalse(run.output().contains("fixture lookup failed"));
        assertTrue(store.readState().getEnrolledLearnerIds().isEmpty());
    }

    private static CliRun run(InMemoryOfferingStore store, String input) {
        StringWriter output = new StringWriter();
        CourseEnrollmentCli cli = new CourseEnrollmentCli(
                new StringReader(input), output, new CourseEnrollmentService(store));
        cli.runSession();
        return new CliRun(output.toString(), store);
    }

    private static InMemoryOfferingStore store(int capacity, Map<String, Boolean> eligibility) {
        return new InMemoryOfferingStore(capacity, new EligibilityFixture(eligibility));
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private record CliRun(String output, InMemoryOfferingStore store) {
    }

    private static final class ThrowingEligibilityFixture extends EligibilityFixture {
        private ThrowingEligibilityFixture() {
            super(Map.of("learner-a", true));
        }

        @Override
        public Optional<Boolean> lookupEligibility(String learnerId) {
            throw new IllegalStateException("fixture lookup failed");
        }
    }
}
