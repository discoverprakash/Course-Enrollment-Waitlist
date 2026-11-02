# Course Enrollment & Waitlist Management

A Java 17 command-line application that manages course enrollment, capacity limits, and FIFO waitlisting with prerequisite eligibility checks.

The application was developed as an AI-assisted coding assessment, using incremental implementation, code review, and automated verification.

## Features

- Enroll eligible learners when seats are available.
- Automatically waitlist eligible learners when the course is full.
- Prevent duplicate enrollment and waitlist entries.
- Drop enrolled learners and promote the first currently eligible waitlisted learner.
- Skip but retain ineligible waitlisted learners during promotion.
- Validate learner IDs and distinguish unknown from ineligible learners.
- Preserve state when prerequisite evaluation fails.
- Support concurrent enrollment and drop operations within a single service instance.
- Provide an interactive CLI for enrollment, drop, and status operations.

## Technology Stack

- Java 17
- Apache Maven 3.9.x (recommended)
- JUnit 5
- Java standard library concurrency utilities
- In-memory storage

No database, REST API, external service, or additional runtime framework is required.

## Prerequisites

Install and configure:

1. JDK 17 or later, with Java 17 used for development and verification.
2. Apache Maven 3.9.x or another compatible Maven version.

Verify your environment:

```bash
java --version
mvn --version
```

Ensure Maven reports that it is running with the intended JDK.

## Build and Test

From the project root, where `pom.xml` is located:

```bash
mvn clean test
```

To compile, run tests, and package the application:

```bash
mvn clean package
```

The Maven Surefire test reports are generated under:

```text
target/surefire-reports/
```

## Run the Application

After building, start the interactive CLI.

**Windows PowerShell / Command Prompt:**

```powershell
java -cp target\classes com.courseenrollment.cli.Main
```

**macOS / Linux:**

```bash
java -cp target/classes com.courseenrollment.cli.Main
```

The application displays a command prompt and accepts commands until `exit` or end-of-input.

## Demo Configuration

The application initializes one course offering with capacity **3**.

The following learner IDs are predefined and eligible:

| Learner ID | Prerequisite eligibility |
|---|---|
| learner-a | Eligible |
| learner-b | Eligible |
| learner-c | Eligible |
| learner-d | Eligible |

Any learner ID not present in the fixture is treated as unknown.

The demo fixture is defined in `Main.java`. Tests can initialize offerings with different positive capacities and eligibility configurations.

## Available Commands

| Command | Description |
|---|---|
| `enroll <learnerId>` | Enroll a learner or join the waitlist |
| `drop <learnerId>` | Drop an enrolled learner and attempt promotion |
| `status` | Display course capacity, enrollment, available seats, and waitlist counts |
| `help` | Display available commands |
| `exit` | Exit the application |

Invalid commands or arguments produce an error message without terminating the session.

## Example Workflow

Enter the following commands in order:

```text
status
enroll learner-a
enroll learner-b
enroll learner-c
enroll learner-d
enroll learner-d
drop learner-b
status
exit
```

Expected behavior:

1. The initial course has three available seats.
2. Learners A, B, and C are enrolled.
3. Learner D joins the waitlist at position 1.
4. Repeating D's enrollment request returns the existing waitlist position without creating a duplicate.
5. Dropping B releases a seat and promotes D.
6. The final status shows three enrolled learners, zero available seats, and zero waitlisted learners.

## Business Rules

### Enrollment

- A known, eligible learner is enrolled when capacity is available.
- If the course is full, the eligible learner joins the FIFO waitlist.
- An already enrolled or waitlisted learner receives their existing status without state mutation.
- Unknown or ineligible learners cannot join.
- Invalid learner IDs are rejected.

### Drop and Promotion

- Only enrolled learners can be dropped.
- Dropping a waitlisted learner does not cancel their waitlist entry.
- A successful drop scans the waitlist in FIFO order.
- Eligibility is rechecked before promotion.
- Ineligible waitlisted learners are skipped but retained in their original order.
- Only the first currently eligible learner is promoted.
- If nobody is eligible, the seat remains available.
- Promotion occurs only after a successful drop.

### Consistency and Failure Handling

- Enrollment and drop operations are serialized using a service-level lock.
- Course state is represented by immutable snapshots.
- State changes are published as a single atomic reference replacement.
- Unexpected prerequisite evaluation failures do not commit partial state changes.
- Locks are released on failure paths.

## Project Structure

```text
src/
  main/java/com/courseenrollment/
    cli/
      Main.java
      CourseEnrollmentCli.java
    domain/
      CourseOfferingState.java
      OfferingStatus.java
    service/
      CourseEnrollmentService.java
      EnrollmentResult.java
      DropResult.java
    store/
      EligibilityFixture.java
      InMemoryOfferingStore.java
  test/java/com/courseenrollment/
    cli/
    domain/
    service/
    store/

docs/
  architecture-options.md
  HLD.md
  LLD.md
  test-plan.md

requirement.md
pom.xml
README.md
```

## Testing

JUnit 5 tests cover:

- State initialization and immutability.
- Capacity and input validation.
- Enrollment, duplicate requests, and waitlisting.
- FIFO promotion and eligibility changes.
- Drop behavior and waitlist preservation.
- Concurrent enrollment and drop scenarios.
- Failure atomicity and recovery.
- CLI commands, invalid input, and end-to-end workflows.

Run all tests using:

```bash
mvn clean test
```

The latest reported verification completed 53 tests with zero failures, errors, or skipped tests.

## Design Decisions and Limitations

- **Single offering:** The application manages one course offering.
- **In-memory state:** Data is not persisted and resets when the application restarts.
- **Single process:** Concurrency safety is designed for operations through the same service instance, not distributed deployments.
- **Static demo eligibility:** The CLI uses a predefined eligibility fixture. Tests can simulate changes and failures.
- **No waitlist cancellation command:** Dropping a waitlisted learner returns `NOT_ENROLLED` without removing their waitlist entry.
- **No automatic eligibility-triggered promotion:** Promotion is attempted only after a successful drop.

These constraints keep the solution focused on the assessment requirements.

## Design Documentation

- `requirement.md` — Functional requirements and assessment context.
- `docs/architecture-options.md` — Considered design alternatives and tradeoffs.
- `docs/HLD.md` — High-level architecture.
- `docs/LLD.md` — Detailed design and component contracts.
- `docs/test-plan.md` — Functional, concurrency, failure, and CLI verification scenarios.

## AI-Assisted Development

GitHub Copilot was used to assist with design exploration, implementation, test generation, and review.

Development was performed incrementally, with explicit scope boundaries and verification checkpoints. Generated code and tests were reviewed against the requirements and design documents, and Maven tests were run to validate the implementation.

The detailed AI usage record should be maintained separately in `docs/AI-USAGE-LOG.md`.