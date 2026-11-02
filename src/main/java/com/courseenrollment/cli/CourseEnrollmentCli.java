package com.courseenrollment.cli;

import com.courseenrollment.domain.OfferingStatus;
import com.courseenrollment.service.CourseEnrollmentService;
import com.courseenrollment.service.DropResult;
import com.courseenrollment.service.EnrollmentResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;

import static com.courseenrollment.service.DropResult.Status.DROPPED;
import static com.courseenrollment.service.EnrollmentResult.Status.ENROLLED;
import static com.courseenrollment.service.EnrollmentResult.Status.WAITLISTED;

public final class CourseEnrollmentCli {
    private static final String INVALID_REQUEST_MESSAGE =
            "INVALID_REQUEST: Invalid command, arguments, or learner ID. Type help for command formats.";
    private static final String INTERNAL_ERROR_MESSAGE =
            "INTERNAL_ERROR: The operation failed before commit; no state was changed.";
    private static final String HELP_TEXT = String.join(System.lineSeparator(),
            "enroll <learnerId>  Enroll an eligible learner or join the waitlist.",
            "drop <learnerId>    Drop an enrollment and promote the first eligible waiter.",
            "status              Show capacity and enrollment/waitlist counts.",
            "help                Show command formats.",
            "exit                End the session.");

    private final BufferedReader input;
    private final PrintWriter output;
    private final CourseEnrollmentService service;

    public CourseEnrollmentCli(Reader input, Writer output, CourseEnrollmentService service) {
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = new PrintWriter(Objects.requireNonNull(output, "output"));
        this.service = Objects.requireNonNull(service, "service");
    }

    public void runSession() {
        while (true) {
            if (!writePrompt()) {
                reportOutputFailure(false);
                return;
            }

            String line;
            try {
                line = input.readLine();
            } catch (IOException inputFailure) {
                writeLine("INPUT_ERROR: Input could not be read.");
                return;
            }

            if (line == null) {
                writeLine("Goodbye.");
                return;
            }
            if (!handleCommand(line)) {
                return;
            }
        }
    }

    private boolean handleCommand(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return writeLine(INVALID_REQUEST_MESSAGE);
        }

        String[] arguments = trimmed.split("\\s+");
        return switch (arguments[0]) {
            case "enroll" -> handleEnroll(arguments);
            case "drop" -> handleDrop(arguments);
            case "status" -> handleStatus(arguments);
            case "help" -> handleHelp(arguments);
            case "exit" -> handleExit(arguments);
            default -> writeLine(INVALID_REQUEST_MESSAGE);
        };
    }

    private boolean handleEnroll(String[] arguments) {
        if (arguments.length != 2 || isInvalidLearnerId(arguments[1])) {
            return writeLine(INVALID_REQUEST_MESSAGE);
        }

        EnrollmentResult result;
        try {
            result = service.enroll(arguments[1]);
        } catch (RuntimeException unexpectedFailure) {
            result = EnrollmentResult.of(EnrollmentResult.Status.INTERNAL_ERROR);
        }
        return writeOutcome(formatEnrollmentResult(arguments[1], result),
                result.status() == ENROLLED || result.status() == WAITLISTED);
    }

    private boolean handleDrop(String[] arguments) {
        if (arguments.length != 2 || isInvalidLearnerId(arguments[1])) {
            return writeLine(INVALID_REQUEST_MESSAGE);
        }

        DropResult result;
        try {
            result = service.drop(arguments[1]);
        } catch (RuntimeException unexpectedFailure) {
            result = DropResult.of(DropResult.Status.INTERNAL_ERROR);
        }
        return writeOutcome(formatDropResult(arguments[1], result), result.status() == DROPPED);
    }

    private boolean handleStatus(String[] arguments) {
        if (arguments.length != 1) {
            return writeLine(INVALID_REQUEST_MESSAGE);
        }

        OfferingStatus status;
        try {
            status = service.getStatus();
        } catch (RuntimeException unexpectedFailure) {
            return writeOutcome(INTERNAL_ERROR_MESSAGE, false);
        }
        String message = String.join(System.lineSeparator(),
                "Capacity: " + status.capacity(),
                "Enrolled: " + status.enrolledCount(),
                "Available seats: " + status.availableSeats(),
                "Waitlisted: " + status.waitlistCount());
        return writeOutcome(message, false);
    }

    private boolean handleHelp(String[] arguments) {
        if (arguments.length != 1) {
            return writeLine(INVALID_REQUEST_MESSAGE);
        }
        return writeLine(HELP_TEXT);
    }

    private boolean handleExit(String[] arguments) {
        if (arguments.length != 1) {
            return writeLine(INVALID_REQUEST_MESSAGE);
        }
        writeLine("Goodbye.");
        return false;
    }

    private boolean writeOutcome(String message, boolean stateWasCommitted) {
        if (writeLine(message)) {
            return true;
        }
        reportOutputFailure(stateWasCommitted);
        return false;
    }

    private boolean reportOutputFailure(boolean stateWasCommitted) {
        String message = stateWasCommitted
                ? "OUTPUT_ERROR: The operation was committed, but its result could not be displayed."
                : "OUTPUT_ERROR: No state change was made; the result could not be displayed.";
        writeLine(message);
        return false;
    }

    private boolean writePrompt() {
        output.print("> ");
        output.flush();
        return !output.checkError();
    }

    private boolean writeLine(String message) {
        output.println(message);
        output.flush();
        return !output.checkError();
    }

    private static String formatEnrollmentResult(String learnerId, EnrollmentResult result) {
        return switch (result.status()) {
            case ENROLLED -> "ENROLLED: " + learnerId + " is enrolled.";
            case ALREADY_ENROLLED -> "ALREADY_ENROLLED: " + learnerId + " is already enrolled.";
            case WAITLISTED -> "WAITLISTED: " + learnerId + " joined at position "
                    + result.waitlistPosition().orElseThrow() + ".";
            case ALREADY_WAITLISTED -> "ALREADY_WAITLISTED: " + learnerId
                    + " is already waitlisted at position " + result.waitlistPosition().orElseThrow() + ".";
            case INELIGIBLE -> "INELIGIBLE: " + learnerId + " does not satisfy the prerequisite.";
            case UNKNOWN_LEARNER -> "UNKNOWN_LEARNER: " + learnerId
                    + " is not in the learner eligibility fixture.";
            case INVALID_REQUEST -> INVALID_REQUEST_MESSAGE;
            case INTERNAL_ERROR -> INTERNAL_ERROR_MESSAGE;
        };
    }

    private static String formatDropResult(String learnerId, DropResult result) {
        return switch (result.status()) {
            case DROPPED -> result.promotedLearnerId()
                    .map(promotedLearnerId -> "DROPPED: " + learnerId + " dropped; "
                            + promotedLearnerId + " was promoted.")
                    .orElse("DROPPED: " + learnerId
                            + " dropped; no eligible learner was available, so the seat is open.");
            case NOT_ENROLLED -> "NOT_ENROLLED: " + learnerId + " has no active enrollment.";
            case UNKNOWN_LEARNER -> "UNKNOWN_LEARNER: " + learnerId
                    + " is not in the learner eligibility fixture.";
            case INVALID_REQUEST -> INVALID_REQUEST_MESSAGE;
            case INTERNAL_ERROR -> INTERNAL_ERROR_MESSAGE;
        };
    }

    private static boolean isInvalidLearnerId(String learnerId) {
        return learnerId == null
                || learnerId.isBlank()
                || learnerId.chars().anyMatch(Character::isWhitespace);
    }
}
