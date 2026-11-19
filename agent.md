# Repository coding standards

These instructions apply to future changes in this repository. Prefer the
existing project patterns and keep changes focused.

## Project structure as it exists today

```text
src/main/java/MoLSP/go/ke/Social_Assistance_Fund/
├── configs/       Spring configuration and bound configuration properties
├── controllers/   HTTP routes for auth, administration, applications and geography
├── dtos/          Request/response DTOs, wrappers and pagination models
├── enums/         Roles, funds, application states and shared enum contract
├── exceptions/    Domain exceptions and global HTTP exception mapping
├── filters/       Servlet filters, including request correlation
├── listeners/    JPA lifecycle listeners
├── logging/       Logging converters and masking integration
├── models/        JPA entities for users, funds, applicants and applications; shared Base
├── repositories/  Spring Data JPA repositories and specifications
├── security/      JWT, user-details loading and security error handling
├── services/      Business rules, transactions and data access
└── utils/         Response, pagination, seeders, slug, phone and PII utilities

src/main/resources/
├── db/migration/  Versioned Flyway SQL migrations
├── application.properties
├── geography.yml  County, sub-county and ward source data
└── logback-spring.xml

src/test/java/     Tests organized by the production package they cover
docs/              Design, deployment, API and reference-data documentation
```

Keep the current package names and layout. Add a package only when a clear
responsibility needs it. Put each new application use case in a service; do not
put SQL, repository access, business rules or transaction boundaries in a
controller.

## Controller and service responsibilities

- Controllers define routes, bind and validate HTTP inputs, obtain the request ID,
  call a service, and return the shared response format. Keep them thin.
- Services own queries, repository access, business validation, workflow rules,
  and transaction boundaries. Services should not depend on controllers.
- Use request DTOs for writes and response DTOs for reads; never accept or return
  JPA entities directly from an API.
- Use the existing `ResponseHandler` and `ResponseWrapper<T>`. Services return
  the endpoint response through `ResponseHandler` when that keeps controllers to
  direct service delegation; do not duplicate response construction.
- Every API response, including paginated responses and single-object responses,
  must use `ResponseWrapper<T>` as the outer JSON object. A paginated payload
  (`PaginatedResponse<T>`) belongs in `ResponseWrapper.data`; its `content` and
  page metadata must never be returned at the JSON root. A non-paginated
  response puts the single DTO/value directly in `data`.
- Inject `ResponseHandler` into controllers. For each request, obtain the
  correlation ID with `responses.requestId(request)` and pass it to the service.
  Services pass that ID to `ResponseHandler.success(...)`,
  `ResponseHandler.created(...)`, `ResponseHandler.paginated(...)`, or the
  appropriate error helper. These helpers populate `requestId` in the wrapper
  and the `X-Request-ID` response header. `CorrelationIdFilter` validates an
  incoming `X-Request-ID` or creates one, places it in MDC for request-scoped
  logging, and clears/restores MDC after dispatch. Do not generate IDs
  independently in controllers/services or omit the ID from response handling.
- Do not add broad `try/catch` blocks or convert failures into success-shaped
  values. Let expected domain exceptions reach `GlobalExceptionHandler`, which
  maps them consistently. Log errors with safe metadata only; never log PII,
  request bodies, tokens, or raw database errors.
- Keep authorization enforced by Spring Security and/or method security. A route
  must not be made public merely to simplify a test.

### Validation

- Use an ordinary `if` for a single validation.
- When a controller/service method has multiple independent early-return
  validations, use the ternary-chain pattern below with the appropriate
  `ResponseHandler` method. Keep the chain readable and avoid hiding
  side-effects inside conditions.
- For domain operations already expressed with typed exceptions, preserve that
  pattern and let the global exception handler produce the HTTP error.
- Validate request DTOs with Jakarta Bean Validation where applicable.

```java
ResponseEntity<ResponseWrapper<Void>> validationResult = entity == null
        ? responses.notFound("Entity not found", requestId)
        : entity.getDeletedAt() != null
        ? responses.badRequest("Entity is archived", requestId)
        : request.getAmount() == null || request.getAmount() <= 0
        ? responses.badRequest("Invalid amount", requestId)
        : null;

if (validationResult != null) return validationResult;
```

Available response methods include `badRequest`, `unauthorized`, `forbidden`,
`notFound`, `conflict`, `internalError`, `success`, `created`, and `paginated`.

## Pagination

- Every endpoint returning a collection is paginated, including lookup
  collections. Use the `PaginatedResponse<T>` wrapper inside `ResponseWrapper`.
- Accept optional `pageNo`, `pageSize`, `sortBy`, and `sortDirection`.
- Defaults are page 0, page size 2, and descending sort; page size must be from 1
  through 100.
- Use `PaginationUtil.buildPageable(...)` and `ResponseHandler.paginated(...)`.
- Keep pagination metadata inside the wrapper's `data` field:

  ```json
  {
    "success": true,
    "message": "Records retrieved",
    "data": {
      "content": [],
      "pageNo": 0,
      "pageSize": 2,
      "totalElements": 0,
      "totalPages": 0,
      "last": true
    },
    "requestId": "..."
  }
  ```

- Use JPA `Page`/`Pageable` and Specifications for repository-backed dynamic
  filtering. For JDBC-backed lists, apply a parameterized `LIMIT`/`OFFSET`, run a
  matching count query, and return a `Page`.
- Never interpolate user-supplied SQL identifiers. Map requested sort fields
  through an explicit allowlist. Reference data without timestamps should use a
  stable natural-key sort (normally `name`).
- Keep all filters optional and compose them in the service layer.

## Archive and soft delete

- For mutable resources, deletion is an archive/unarchive operation, exposed as
  `PATCH /{resource}/{id}/archive`; do not physically delete application data
  through normal API routes.
- Set/clear `deletedAt` and `deletedBy` together. Reject edits to archived
  records unless the operation explicitly restores them.
- List APIs default to active records. Where archived-record retrieval is
  supported, accept optional `isArchived`; `true` selects archived records and
  `false` selects active records.
- The current `Base` model uses nullable `deletedAt`/`deletedBy` and Hibernate's
  `@SQLRestriction("deleted_at is null")`; it does not have a Boolean `deleted`
  field and currently filters archived records out globally. Follow this schema;
  exposing archived rows requires an explicit persistence/query design change.
- Reference/lookup data has no end-user archive API unless a specific requirement
  calls for one.

## Serialization and API models

- Use Jackson only; do not add Gson. Use the JSON version managed by the
  repository's Spring Boot 4.1 dependency management; do not introduce Jackson
  2.x API types into request/response DTOs.
- Configure Jackson centrally for pretty printing, module discovery,
  unknown-property tolerance on deserialization, and ISO-8601 date strings
  rather than numeric timestamps.
- Keep request/response models separate from entities. Use `Map<String, Object>`
  for JSON/JSONB payloads rather than a Jackson tree type in persistence models.
- Responses use `ResponseWrapper<T>` and ISO-8601 timestamps. Omit null response
  fields where appropriate.
- If custom Jackson behavior is needed, configure it centrally rather than
  annotating or configuring individual controllers inconsistently.

## Persistence and auditing

- Every persisted entity extends `models.Base`, uses Flyway-managed schema, and
  retains optimistic locking where applicable.
- Existing auditing is implemented by Spring Data JPA auditing in
  `configs.JpaAuditingConfig` and `AuditingEntityListener`. It derives the actor
  from an enabled authenticated `User`; unauthenticated writes fail. Do not
  silently substitute `SYSTEM` or trust caller-supplied audit actors.
- Keep transactions in services. Use JDBC for startup reference seeding where
  no authenticated actor exists, and validate seed input before writing.
- Make migrations additive and versioned. Do not edit an already-applied
  migration; add a new migration for schema changes.
- Preserve foreign keys, uniqueness constraints, and transactional integrity.
- **Exception — append-only records:** `application_audit_events`,
  `notification_outbox` and `notification_delivery_attempts` are written with
  `JdbcTemplate`, not `Base` entities. Audit rows are insert-only (no update or
  delete code path); outbox rows change only through `NotificationService`'s
  guarded status updates. They carry explicit actor/time columns because the
  scheduler and async workers have no authenticated user. Do not add JPA
  entities/repositories with `save`/`delete` for these tables.

## Application audit and notifications

- `ApplicationAuditService.record(...)` must be called inside the same
  transaction as every application creation, material modification and workflow
  transition. Record event type, application ID, actor (user UUID or
  `PUBLIC_SELF_REGISTRATION`), time, from/to status, reason, field changes and
  request ID. Redact sensitive changed values (identity number, phones) as
  `[REDACTED]`; record only that they changed.
- `NotificationService` is the single owner of applicant communication. Other
  services only call its enqueue methods (`applicationReceived`,
  `returnedForCorrection`, `decision`) inside their transaction; they never call a
  gateway directly. Enqueue is an outbox insert, so a failed or absent provider
  can never roll back an approval/rejection.
- Delivery happens after commit on the `notificationExecutor` (async,
  non-blocking) and from a scheduled poller. Claims use a conditional UPDATE
  with a `claim_token` and lease; completions are guarded by that token.
  `idempotency_key = eventType:auditEventId` is unique to stop duplicate enqueue.
- Gateways implement `notifications.NotificationGateway`. The TechVoyage
  gateway loads only when `COMMS_PROVIDER=techvoyage` and all `COMMS_*` URLs and
  credentials are non-blank; `stub` loads the logging stub. With no gateway the
  app still starts and rows stay `PENDING` (`PROVIDER_NOT_CONFIGURED`) until a
  provider is configured. Never make the gateway a mandatory bean.
- Never log recipients, message bodies, tokens or provider credentials. API
  responses mask recipients. Tests must use a local mock server, never the real
  SMS endpoint.

## Error handling and logging

- Reuse `ResourceNotFoundException`, `DuplicateResourceException`, and
  `GlobalExceptionHandler` for consistent HTTP errors.
- Use `ResponseHandler` for standard success, creation, pagination, and direct
  validation responses.
- Use the request correlation ID and existing PII-safe logging pipeline. Do not
  log secrets, authentication headers, applicant data, or raw request content.

## Tests and verification

- Add focused JUnit 5 tests for service rules, validation, pagination boundaries,
  and controller response shapes.
- Use Testcontainers MySQL for behavior depending on MySQL/Flyway/JPA semantics.
- Run the smallest relevant test set, then the full suite when changes affect
  shared infrastructure, migrations, security, or serialization.
- Keep tests isolated; do not depend on a developer's local `.env` or database.

## Bootstrap and role boundaries

- The first `PLATFORM_ADMIN` is inserted idempotently from the
  `PLATFORM_ADMIN_FIRST_NAME`, `PLATFORM_ADMIN_LAST_NAME`, `PLATFORM_ADMIN_EMAIL`,
  and `PLATFORM_ADMIN_PASSWORD` environment values. Never log the password or
  overwrite an existing account's password/profile at startup.
- `PLATFORM_ADMIN` manages staff accounts, but cannot create `APPLICANT` users.
  Staff creation accepts a temporary password, stores only BCrypt, and returns a
  password-free DTO. Account deletion deactivates the user; preserve at least one
  enabled platform administrator.
- Applicants are records, not login accounts. Create them only with an assisted
  Data Entry Officer application draft or the public self-registration form.
- Public form persistence has no authenticated user. Its short-lived audit context
  records the fixed actor marker `PUBLIC_SELF_REGISTRATION`; do not extend that
  context to other operations. All authenticated writes use the current user's UUID.

## Current application boundaries

Only the existing geography GET lookups (counties, sub-counties and wards) and
`GET /api/social-funds` are public read catalogues. Fund detail, create, update
and archive routes remain `PLATFORM_ADMIN` only. Keep public matchers limited to
these exact paths and GET; do not permit whole resource trees or write methods.
Public clients should omit Authorization headers so stale tokens do not reject
otherwise anonymous requests. No persistence/entity changes are needed for access
policy changes; preserve DTOs, pagination and archive filtering.

The application includes staff login/management, social fund CRUD, county/
sub-county/ward lookups, assisted application drafts, public application
self-registration, approver transitions, an immutable application audit trail,
read-only history/actions/notification-lifecycle endpoints and outbox-based
SMS notifications. Returned applications do not yet have an amendment/
resubmission path by user decision; plan that in a later phase.
Lower geography levels (location, sub-location, village) remain text because no
authoritative hierarchy was supplied. Email notifications (applicants have no email), frontend, password reset,
rate limiting and production-hardening are not implemented; do not imply otherwise.
