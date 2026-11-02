# Course-Enrollment-Waitlist

## Assignment Requirements

### Objective and scope

Build one bounded course-registration feature for a single course offering.
For this assessment, no starter project or existing data model is available. Therefore, we will follow the minimal greenfield implementation path using an in-memory store. Demonstrate clear task decomposition, thoughtful review of AI output, and independent verification. This is a reliable service slice, not a complete registration platform.

Use the starter's data model. If none is provided, a small service or command-line prototype with an in-memory store is sufficient.

### Functional requirements

1. Enroll a learner when capacity is available and the learner satisfies the stated prerequisite rule.
2. When the course is full, add an eligible learner to the waitlist once; duplicate enrollment requests must not create duplicate records.
3. When an enrolled learner drops, open a seat and promote the earliest eligible waitlisted learner according to the documented eligibility policy.
4. Do not enroll or promote an ineligible learner. State and consistently apply the policy for an ineligible learner encountered during promotion.
5. Return clear results for invalid requests and duplicate drops without corrupting enrollment, capacity, or waitlist state.

### Engineering and submission requirements

- Test available and full capacity, duplicate requests, FIFO promotion, prerequisite failure, and invalid or repeated drops. If the starter uses concurrent requests, preserve its transaction or locking pattern and add a focused concurrency test where practical.
- Use AI for at least one engineering activity. Record the prompts used, relevant AI response or summary, whether each suggestion was accepted, edited, or rejected, and how the resulting work was verified.
- Submit runnable code and focused automated tests, a proportionate design specification, an AI prompt and use record with verification evidence, brief setup and test instructions, and a concise summary of in-scope limitations or follow-up risks.
- The design specification should describe the service boundary, relevant data/API design, enrollment and capacity invariants, waitlist-promotion rule, concurrency or persistence approach, and key assumptions or tradeoffs.
- Independently review and verify AI-generated suggestions. Do not include credentials, secrets, or unnecessary sensitive data in prompts or prompt records.
- Prioritize correct capacity and queue behavior, focused tests, understandable changes, and evidence of independent review over feature count or extra infrastructure.

### Scope boundaries

- Implement only one offering's enrollment, drop, and waitlist-promotion slice; do not build the full registration product.
- Do not add a frontend, course catalog, payments, identity integrations, multi-term registration, deployment, or a new database unless the starter project requires it.
- Do not implement separate greenfield, brownfield, and ambiguous scenarios. Work in the supplied starter; otherwise use the minimal service or CLI fallback described above.
- State the prerequisite and waitlist assumptions; do not build a configurable policy engine.

### Suggested timebox

Suggested effort: 7-8 hours. Focus on the scoped workflow, tests, design specification, and AI-use record. Use the AI-Assisted scorecard in Course-Registration-Rubric-Score.md, rating evidence relevant to this task's scope.

## Agreed Design Decisions

These decisions clarify behavior where the assignment leaves details open; they do not add features beyond the assignment scope.

1. A learner must satisfy the prerequisite to enroll or join the waitlist. Check eligibility again when considering the learner for promotion.
2. If a learner is already enrolled, a repeated enrollment request returns the existing enrollment state and creates no duplicate.
3. If a learner is already waitlisted, a repeated request returns their existing waitlist position and creates no duplicate entry.
4. A learner who dropped an enrollment may request enrollment again. Treat the new request as a fresh request; if waitlisted, it receives a new FIFO position.
5. FIFO order is based on when each waitlist request is accepted.
6. During promotion, skip an ineligible learner for that promotion while retaining their original relative position. Continue checking later waitlisted learners for eligibility.
7. If no eligible learner is available for promotion, leave the seat open.
8. An invalid or repeated drop returns a clear result and changes no state.
9. Dropping an enrollment and any resulting promotion are one consistent state transition. If the operation fails before completion, neither the drop nor a partial promotion is applied.

## State Invariants

- The number of enrolled learners never exceeds the offering's capacity.
- A learner has at most one active enrollment or waitlist entry for the offering; repeated requests do not add records.
- Only a learner who satisfies the prerequisite may be newly enrolled, added to the waitlist, or promoted.
- Waitlist entries retain their accepted-request order. Skipping an ineligible learner for a promotion does not change that learner's position relative to others who remain waiting.
- A successful drop removes the learner's enrollment and opens one seat before promotion is considered. An invalid or repeated drop leaves enrollment, capacity, and waitlist state unchanged.
- If promotion finds no eligible waitlisted learner, the available seat remains open.
- Dropping an enrollment and promoting a learner are applied consistently as one state transition. If that operation fails, the pre-operation enrollment and waitlist state is preserved; a partial drop or promotion is not left behind.
