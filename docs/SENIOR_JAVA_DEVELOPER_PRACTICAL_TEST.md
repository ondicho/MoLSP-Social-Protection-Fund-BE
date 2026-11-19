# Senior Java Developer Practical Test

**Development Pathways — A DT Global Company**\
**Open-book practical test**\
**Maximum candidate effort:** 8 hours\
**Submission deadline:** 5:00 p.m. Nairobi time, 20 November 2026

## Assessment information

| Item | Requirement |
|---|---|
| Purpose | Assess senior-level Java engineering judgement through a focused, working vertical slice. |
| Core stack | Java, Spring Boot, Spring Data JPA/Hibernate, MySQL and Vue.js with Pebble templates. |
| Time-box | Spend no more than 8 hours. State actual time spent and stop when the time-box is reached. |
| Priority | Correctness, data integrity, security, tests and design reasoning take priority over feature volume. |
| Submission | Source code, database migrations, tests, README and concise design note. |

## 1. Background

One of our clients, the Ministry of Labour and Social Protection (MoLSP), is establishing a Social Assistance Fund. The Fund will consolidate financial resources from state and non-state actors and provide timely, predictable support to needy and vulnerable people. Applicants will be identified on demand and assessed according to vulnerability and income status. An applicant may apply for one or more social assistance programmes. Once eligibility has been determined, the applicant will be informed by email or SMS.

The National Social Protection Secretariat administers the Fund and has developed the application form in Annex 1. An applicant may complete the form online, or a Data Entry Officer may digitise a paper form submitted at a sub-county office.

## 2. Assessment objective and time-box

Build a focused vertical slice of the Social Assistance application workflow. The exercise is intentionally time-boxed to eight hours. Assessors do not expect a production-complete MIS. The submission should show how a Senior Java Developer protects data integrity, models workflow rules, designs maintainable APIs, tests significant behaviour and communicates trade-offs.

- Do not exceed eight hours of candidate effort.
- Record the approximate time spent and features completed in the README.
- If time expires, submit the working state and document incomplete items, risks and the next implementation steps.
- A smaller coherent implementation with strong tests and clear decisions will score better than a broad but fragile solution.
- Use of documentation, IDE assistance and normal open-book resources is permitted. The candidate must be able to explain every submitted design and code decision.

## 3. Required technology stack

- **Backend:** Java 17 or later and Spring Boot. If Spring Boot 2.x is used to reflect the legacy client platform, explain the choice and compatibility implications.
- **Data access:** Spring Data JPA/Hibernate.
- **Database:** MySQL, with versioned migrations using Flyway or Liquibase.
- **Frontend:** Vue.js with Pebble templates. The interface may be deliberately minimal.
- **Build:** Maven or Gradle.
- **API documentation:** OpenAPI/Swagger.
- **Testing:** JUnit 5, Mockito where appropriate, and Testcontainers for MySQL integration tests.
- **Security:** Spring Security with seeded local test users or an equivalent documented demonstration method.
- **Operations:** Spring Boot Actuator for health and metrics.

## 4. Functional scope

### 4.1 Applicant and application data

- Capture first name, middle name and last name separately.
- Capture ID card or passport number as an alphanumeric value of no more than 20 characters and enforce an appropriate uniqueness rule.
- Capture application date, applicant signed date, postal address, physical address and one or more telephone contacts.
- Allow an application to include one or more social assistance programmes.
- Capture the official-use details separately: officer name, designation, signature/reference and signed date.
- Enforce that the applicant signed date cannot precede the application date and that the information collector signed date cannot precede either date.
- Model geography as **County > Sub-county > Location > Sub-location > Village**, using integer primary and foreign keys and cascading user selection.
- Store configurable values, including sex, marital status, programme and application status, in appropriate lookup tables.

### 4.2 Mandatory workflow

Implement and enforce the state model in the service/domain layer, not only in the user interface.

| Current state | Permitted action | Next state | Principal rule |
|---|---|---|---|
| `DRAFT` | Submit | `SUBMITTED` | Creator may edit; submission requires all mandatory fields and at least one programme. |
| `SUBMITTED` | Start review | `UNDER_REVIEW` | Approver only. |
| `UNDER_REVIEW` | Approve | `APPROVED` | Approver only; official-use section must be complete. |
| `UNDER_REVIEW` | Reject | `REJECTED` | Approver only; rejection reason is mandatory. |
| `UNDER_REVIEW` | Return for correction | `RETURNED_FOR_CORRECTION` | Approver only; correction reason is mandatory. |
| `RETURNED_FOR_CORRECTION` | Resubmit | `SUBMITTED` | Creator may amend and resubmit. |

- `SUBMITTED` and `UNDER_REVIEW` applications are read-only to the Data Entry Officer.
- `APPROVED` and `REJECTED` applications cannot be edited through the normal update endpoint.
- Invalid state transitions must return HTTP `409 Conflict` with a consistent error response.
- Every transition must record actor, timestamp, previous state, new state and reason where relevant.

### 4.3 Search and user interface

- Provide a minimal Vue.js interface to create/edit a draft, view application details, list/search applications and perform approver actions.
- Implement cascading geographic dropdowns and programme multi-selection.
- Filter applications by applicant name, date or date range, status, programme and each geographic level.
- Use server-side pagination with a default page size of 2 and a maximum page size of 100.
- Display validation, authorisation, conflict and API errors clearly.

## 5. Senior-level technical requirements

### 5.1 API design and validation

- Use resource-oriented endpoints and appropriate HTTP methods.
- Use meaningful HTTP status codes, including `201`, `204`, `400`, `401`, `403`, `404` and `409` where applicable.
- Use request/response DTOs rather than exposing persistence entities directly.
- Provide a consistent error model. RFC 7807 Problem Details is recommended.
- Return field-level validation errors and ISO 8601 dates/timestamps.
- Document sorting, pagination and the decision on API versioning in OpenAPI/Swagger.
- Apply a global exception-handling strategy without leaking stack traces or implementation details.

### 5.2 Concurrency and data integrity

- Implement optimistic locking for applications, for example with a JPA `@Version` field.
- Reject a stale conflicting update with HTTP `409 Conflict`.
- Demonstrate the conflict through an automated test in which two clients attempt to update the same application version.
- Use database constraints and transactions to protect rules that must remain true when requests run concurrently.
- Ensure submission and approval retries do not create duplicate state transitions, audit events or notification requests.
- Document the chosen idempotency approach.

### 5.3 Security and privacy

- Implement authentication and role-based authorisation for `DATA_ENTRY_OFFICER` and `APPROVER`.
- Only an `APPROVER` may start review, approve, reject or return an application.
- Unauthenticated requests to protected endpoints must return `401`; insufficient privileges must return `403`.
- Hash passwords using BCrypt or Argon2. Do not store or log plain-text passwords.
- Externalise credentials and secrets. Provide example configuration without real secrets.
- Configure CORS explicitly and validate input lengths and formats.
- Do not log tokens, passwords or complete ID/passport numbers. Mask sensitive identifiers in operational logs.
- Explain personal-data retention, access and logging considerations in the design note.

### 5.4 Audit and notifications

- Create an immutable audit record for application creation, material modification and every workflow transition.
- Record event type, application identifier, actor, timestamp and relevant before/after or transition information.
- When an application is approved or rejected, create an email or SMS notification request behind a provider interface. A real external provider is not required; provide a stub implementation.
- Ensure that a failed notification does not roll back an already committed approval/rejection.
- Document how retries and duplicate delivery would be controlled. A transactional outbox implementation earns bonus credit but is not mandatory.

### 5.5 Observability and operability

- Expose Spring Boot Actuator health information, including database readiness.
- Use structured or consistently formatted logs and propagate a request/correlation ID.
- Log rejected workflow operations and notification failures without exposing personal data.
- Expose at least two useful metrics, such as applications created, approvals completed, failed approval attempts or API latency.
- Provide a repeatable local startup approach. Docker Compose is recommended but not mandatory.

## 6. Database design

Design a MySQL schema normalised to Third Normal Form. Submit versioned migrations and seed data sufficient to demonstrate the workflow, roles, parameters and geographic hierarchy.

- Use integer primary and foreign keys.
- Apply appropriate `NOT NULL`, `UNIQUE`, `CHECK` where supported, and referential constraints.
- Add indexes justified by the required search and workflow queries.
- Provide at least one useful database view or stored procedure where technically defensible. Alternatively, explain why the equivalent Java/JPA design is preferable.
- Include a concise entity-relationship diagram or a clearly readable schema diagram in the design note.

## 7. Automated testing

Testing must protect material behaviour rather than merely increase the test count. Include the following as a minimum:

- Unit tests for workflow transitions, validation and significant service-layer rules.
- MySQL repository/integration tests using Testcontainers.
- API/controller tests for creation, submission, approval, an invalid transition, unauthorised approval, stale-version conflict, filtering and pagination.
- At least one test demonstrating transactional rollback or atomicity.
- A test confirming that a retry does not create a duplicate workflow/audit/notification event.
- Instructions for running the tests and the final test result.
- A short note explaining important scenarios not tested within the eight-hour time-box.

## 8. Mandatory design note and technical walkthrough

Submit a design note of no more than two pages covering:

- Component and deployment view.
- Principal domain entities and relationships.
- Transaction boundaries and workflow enforcement.
- Authentication and authorisation.
- Optimistic locking and idempotency.
- Audit and notification failure handling.
- Scaling and performance assumptions.
- Sensitive-data handling.
- Three material trade-offs made because of the time-box.
- Known limitations and the next three production-hardening steps.

**Post-submission walkthrough:** Shortlisted candidates may be invited to a 30-minute technical walkthrough. The candidate should be prepared to explain a design decision, modify a business rule, diagnose a failing test and describe how the solution would handle ten times the initial load.

## 9. Deliverables

1. Java/Spring Boot backend source code.
2. Minimal Vue.js frontend source code.
3. MySQL Flyway or Liquibase migrations and seed data.
4. Automated tests and test-running instructions.
5. `README.md` with prerequisites, setup, build, run and demonstration steps; seeded test users; actual time spent; completed and incomplete scope.
6. OpenAPI/Swagger documentation.
7. Design note of no more than two pages, including the schema/component diagram.
8. Example configuration with no real credentials or secrets.
9. **Optional bonus:** transactional outbox, Excel/PDF report, Docker Compose, CI pipeline or additional production-hardening evidence.

## 10. Submission instructions

Email the source code or an accessible repository/file-transfer link to [recruitment@developmentpathways.co.uk](mailto:recruitment@developmentpathways.co.uk) by **5:00 p.m. Nairobi time on 20 November 2026**.

The submitted version must be the version intended for evaluation. Ensure assessors can access the repository during the assessment period. Do not include production credentials, personal data or third-party secrets.

## 11. Scoring rubric

The practical test is scored out of 100. Mandatory functionality and quality take precedence over bonus features.

| Assessment area | Weight | Scoring evidence |
|---|---:|---|
| Java and Spring Boot engineering | 20 | Idiomatic Java; cohesion; dependency injection; transaction management; exception handling; maintainable layering. |
| Domain model and workflow integrity | 15 | State rules enforced server-side; invariants; valid and invalid transitions; auditability. |
| Database and data integrity | 15 | 3NF design; migrations; constraints; indexes; query quality; optimistic locking. |
| API design | 10 | HTTP semantics; DTOs; validation; stable errors; pagination; OpenAPI. |
| Security and privacy | 10 | Authentication; role enforcement; secret handling; safe logging; sensitive-data controls. |
| Automated testing | 15 | Focused unit and MySQL integration tests; negative cases; concurrency; idempotency; maintainability. |
| Operability and resilience | 5 | Health; logs; correlation IDs; metrics; notification failure handling. |
| Documentation and decisions | 5 | Reproducible setup; concise design; assumptions; trade-offs; honest limitations. |
| Code quality and usability | 5 | Naming; readability; duplication control; usable minimal UI and clear user feedback. |
| **Total** | **100** | |

### 11.1 Decision bands

| Score | Indicative outcome |
|---:|---|
| 85–100 | Strong senior-level performance |
| 70–84 | Meets senior-level expectations |
| 60–69 | Strong mid-level performance; senior-level gaps remain |
| Below 60 | Does not meet the role’s technical threshold |

### 11.2 Mandatory pass conditions

Regardless of the numerical score, the submission will not meet the Senior Java Developer threshold if any of the following applies:

- The solution cannot be built or run using the README.
- Approval is not protected by the `APPROVER` role.
- Invalid workflow transitions are accepted.
- Credentials or secrets are committed to source control.
- No meaningful automated tests are provided.
- Concurrent updates can silently overwrite applicant data.
- The candidate cannot explain the main architecture and submitted code during the walkthrough.

### 11.3 Bonus credit

Bonus features may strengthen evidence within the relevant rubric category, but the total score remains capped at 100 and bonus work cannot compensate for missing mandatory requirements. Examples include a transactional outbox, CI pipeline, containerised startup and filter-aware Excel/PDF reporting.

## Annex 1: Application form for social assistance

**Republic of Kenya**\
**Social Assistance Fund**\
**Application for Social Assistance**

**To:** The Administrator, Social Assistance Fund

### Applicant’s details

| Field | Entry |
|---|---|
| Application Date (dd/mm/yyyy) | |
| First Name | |
| Middle Name | |
| Last Name | |
| Sex | |
| Date of Birth / Age | |
| Marital Status | |
| Identity Card/Passport Number | |
| County | |
| Sub-county | |
| Location | |
| Sub-location | |
| Village | |
| Postal Address | |
| Physical Address | |
| Telephone Contact(s) | |

### Social assistance programme(s) applied for

- [ ] Orphans and vulnerable children
- [ ] Poor elderly persons
- [ ] Persons with disability
- [ ] Persons in extreme poverty
- [ ] Other: ____________________

I declare that the information provided in this application is true to the best of my knowledge.

| Field | Entry |
|---|---|
| Applicant signature | |
| Applicant signed date | |

### For official use only

| Field | Entry |
|---|---|
| Information collected by / Name | |
| Designation | |
| Signature / reference | |
| Information collector signed date | |
