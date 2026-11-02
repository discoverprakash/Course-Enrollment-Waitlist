package com.courseenrollment.cli;

import com.courseenrollment.service.CourseEnrollmentService;
import com.courseenrollment.store.EligibilityFixture;
import com.courseenrollment.store.InMemoryOfferingStore;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        EligibilityFixture eligibilityFixture = new EligibilityFixture(Map.of(
                "learner-a", true,
                "learner-b", true,
                "learner-c", true,
                "learner-d", true));
        InMemoryOfferingStore store = new InMemoryOfferingStore(3, eligibilityFixture);
        CourseEnrollmentService service = new CourseEnrollmentService(store);
        CourseEnrollmentCli cli = new CourseEnrollmentCli(
                new InputStreamReader(System.in, StandardCharsets.UTF_8),
                new OutputStreamWriter(System.out, StandardCharsets.UTF_8),
                service);

        cli.runSession();
    }
}
