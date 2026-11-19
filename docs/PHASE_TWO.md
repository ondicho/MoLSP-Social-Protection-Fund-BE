# Phase 2: administration, funds and application intake

## Bootstrap and sign-in

On startup, Flyway adds `PLATFORM_ADMIN` and `BootstrapAdminSeeder` creates the
configured first administrator if its email is not already present. Configure
`PLATFORM_ADMIN_FIRST_NAME`, `PLATFORM_ADMIN_LAST_NAME`, `PLATFORM_ADMIN_EMAIL`,
and `PLATFORM_ADMIN_PASSWORD` in the runtime environment. The password must be
at least 12 characters. It is BCrypt-hashed; bootstrap logs contain only a
created/already-present status. An existing account is not silently renamed,
promoted, or password-reset on later startups. A configured email already owned
by a non-platform-admin makes startup fail.

`POST /api/auth/login` accepts email and password and returns an access token,
expiry, and non-sensitive user summary inside `ResponseWrapper.data`. Send the
token as a bearer token. Access tokens expire after the configured
`JWT_ACCESS_EXPIRATION_MS`; refresh, password reset, lockout and login rate
limiting are not yet implemented. Use an approved secret manager in production.

## Staff user management

Every `/api/admin/users` route requires `PLATFORM_ADMIN`.

| Method and route | Purpose |
|---|---|
| `GET /api/admin/users` | Paginated list; optional `search`, `role`, `enabled`, page and sort filters |
| `GET /api/admin/users/{id}` | Retrieve a staff account |
| `POST /api/admin/users` | Create `PLATFORM_ADMIN`, `APPROVER`, or `DATA_ENTRY_OFFICER` |
| `PUT /api/admin/users/{id}` | Update profile/role and optionally set a new temporary password; requires the current `version` |
| `PATCH /api/admin/users/{id}/enabled?enabled=true\|false` | Enable or deactivate an account |
| `DELETE /api/admin/users/{id}` | Deactivate the account; this does not physically delete it |

Create/update requests use first name, last name, email and phone.
Staff phone numbers are normalized to E.164 and globally unique, including
deactivated/archived accounts. Duplicate create/update requests return 409.
The environment-seeded administrator has no phone until one is supplied.
Creation requires an administrator-supplied temporary password (minimum 12
characters); only its BCrypt hash is stored or returned. `APPLICANT` is rejected
by staff management. Applicants are represented by application intake, not user
accounts. At least one enabled platform administrator must remain.

## Social funds

`GET /api/social-funds` is public, including its optional filters and pagination.
All other `/api/social-funds` routes require `PLATFORM_ADMIN`, including fund
detail. The public list returns non-archived funds, not just funds currently
within their application window. Geography GET lookups are also public.

| Method and route | Purpose |
|---|---|
| `GET /api/social-funds` | Paginated list; optional name/slug `search` and fund `type` |
| `GET /api/social-funds/{id}` | Retrieve an active fund |
| `POST /api/social-funds` | Create a fund |
| `PUT /api/social-funds/{id}` | Update a fund; requires the current `version` |
| `PATCH /api/social-funds/{id}/archive` | Toggle archive/unarchive |

Fund fields are name, generated slug, `NATIONAL` or `COUNTY` type,
`FundCoverage`, open/close dates, minimum/maximum amounts (currently assumed
KES), and county IDs. National funds reject county assignments; county funds
require at least one valid county. Minimum and maximum amounts must be
non-negative and minimum must not exceed maximum. The slug is generated from
name plus open and close dates using `StandardUtil.generateSlug`; a numeric suffix
disambiguates collisions. Normal lists include active funds only. Archive changes
preserve the row and its audit metadata.

Create/update requests accept `countyIds`; fund responses instead contain
`counties: [{"id": 30, "name": "Baringo"}]`. National funds return an empty
`counties` array. The same DTO shape is used for create, fetch, update and list.

## Applicant intake and review

Applicants do not receive login accounts in this phase and cannot be created
through staff-user APIs.

| Method and route | Access and behavior |
|---|---|
| `POST /api/applications/self-registration` | Public; validates the completed Annex 1 form and creates a submitted application |
| `POST /api/applications` | `DATA_ENTRY_OFFICER`; creates an assisted draft |
| `GET /api/applications` and `GET /api/applications/{id}` | `PLATFORM_ADMIN`, `APPROVER`, or `DATA_ENTRY_OFFICER`; officers see only applications they created |
| `PUT /api/applications/{id}` | Creating officer only; draft-only update with optimistic `version` |
| `POST /api/applications/{id}/submit` | Creating officer; validates mandatory form data and programme selection |
| `POST /api/applications/{id}/review` | `APPROVER`; submitted to under-review |
| `POST /api/applications/{id}/approve` | `APPROVER`; requires complete official-use fields and valid signed date |
| `POST /api/applications/{id}/reject` | `APPROVER`; requires a reason |
| `POST /api/applications/{id}/return-for-correction` | `APPROVER`; requires a reason |

Every state change is recorded with actor, timestamp, previous/next status and
reason where applicable. Invalid transitions return 409. Self-registration
returns an opaque application ID/status receipt, not applicant PII. Its audit
actor is the fixed `PUBLIC_SELF_REGISTRATION` service marker; authenticated
operations use the authenticated user's UUID. Applicant IDs are unique,
alphanumeric and limited to 20 characters. The application form accepts a
sub-county ID only; county association is resolved from that seeded sub-county.
Phone contacts are normalized to
E.164. Gender is represented by the `Gender` enum; marital status
and application statuses use seeded lookup tables. `programmeCodes` accepts
existing, non-archived social fund slugs, not legacy codes such as `OVC`.
Selections reference fund slugs through a foreign key; fund slug changes cascade
to saved selections. The `programme` search parameter also accepts a fund slug.
No automatic fund eligibility decision is implied by validating its existence.
Date of birth is authoritative
for age calculation when present; a supplied age is used only when date of birth
is absent. Applicant county and sub-county are JPA relationships to the seeded
geography rows, and the service verifies that the selected sub-county belongs to
the selected county.
Applications can be filtered by applicant name/ID, date range, status, programme,
county, sub-county, location, sub-location and village. County filters and
response county IDs are derived through the sub-county's county relationship.

**Deferred by agreement:** applicants have no account or continuation token.
There is no path to amend or resubmit an application returned for correction;
returned records remain locked. Design this correction/resubmission flow in a
later phase before enabling that outcome in production. Location, sub-location
and village remain validated-length text because their authoritative hierarchy
has not been supplied; only county and sub-county are foreign-key lookups.
A browser UI is not implemented in this repository.

## Audit trail and notifications

| Method and route | Access and behavior |
|---|---|
| `GET /api/applications/{id}/history` | Same visibility as detail; paginated audit events, ascending by default (`sortDirection=desc` for newest first) |
| `GET /api/applications/{id}/actions` | Same visibility; actions the caller may perform now, with method, path, next status and required fields |
| `GET /api/applications/{id}/notifications` | Same visibility; paginated notification lifecycle with delivery attempts and masked recipient |

**Audit.** `application_audit_events` stores `CREATED`, `UPDATED` (draft field
diff; identity number and phones recorded only as `[REDACTED]`), `SUBMITTED`,
`REVIEW_STARTED`, `APPROVED` (official-use diff), `REJECTED` and
`RETURNED_FOR_CORRECTION`, each with actor, time, from/to status, reason,
changes and request ID. A monotonic `sequence_no` gives a stable order. Rows are
written in the same transaction as the change and the application never updates
or deletes them. V14 migrated the earlier `application_transitions` rows and
dropped that mutable table. MySQL triggers were rejected because creating them
needs `SUPER` when binary logging is on; for production, grant the application
DB user only `SELECT, INSERT` on `application_audit_events`.

**Notifications.** SMS goes to the applicant's first telephone contact:

- *Application received* — when a self-registration or officer submission
  reaches `SUBMITTED`: name, fund name(s), and that the Ministry will respond on
  eligibility.
- *Returned for correction* — with the reason.
- *Approved / declined* — the approver decision (with reason when declined).

Flow (transactional outbox):

1. The workflow transaction inserts the audit row and a `PENDING`
   `notification_outbox` row. Both commit or neither does.
2. After commit, an `@Async` listener on a bounded `notificationExecutor`
   dispatches the row; the HTTP request never waits for the provider.
3. A scheduler (`NOTIFICATION_POLL_INTERVAL`, default 30 s) picks up due
   `PENDING`/`RETRY_SCHEDULED` rows and expired leases, so work survives restarts,
   a full executor queue or a provider configured later.
4. Provider failure, timeout, unreachable host or `success:false` →
   `RETRY_SCHEDULED` with exponential backoff (`base × 2^(attempt-1)`, capped by
   `NOTIFICATION_RETRY_MAX_DELAY`), until `NOTIFICATION_MAX_ATTEMPTS` (default 8)
   → `FAILED`. Each attempt is stored in `notification_delivery_attempts`.
5. A notification failure never affects the committed application status.

**Duplicate control.** `idempotency_key = eventType:auditEventId` is unique, so a
retried workflow request cannot enqueue twice (the workflow itself is already
protected by optimistic `version` and state checks). Workers claim a row with a
single conditional `UPDATE ... SET status='SENDING', claim_token=?, locked_until=?`
so concurrent workers/instances cannot both send it; completion updates require
the same `claim_token`, so a worker whose lease expired cannot overwrite a newer
attempt. Delivery is **at-least-once**: if the process crashes after the provider
accepted the SMS but before `SENT` is stored, the row is retried after the lease
expires. TechVoyage exposes no idempotency key; a provider that does should be
passed the outbox ID to make delivery effectively-once.

**Provider configuration.** `COMMS_PROVIDER=techvoyage` with `COMMS_AUTH_URL`,
`COMMS_SEND_URL`, `COMMS_USERNAME` and `COMMS_PASSWORD` loads the TechVoyage
gateway (login token cached for `COMMS_TOKEN_TTL`, refreshed once on 401/403).
`COMMS_PROVIDER=stub` logs instead of sending. If any value is missing the app
still starts, logs `notification_provider_status configured=false`, and keeps
rows queued with `last_error=PROVIDER_NOT_CONFIGURED`; they are delivered by
the poller once the provider is configured and the app restarts.

**Deferred:** email delivery (applicants have no email field), provider
delivery-receipt webhooks, an admin re-queue endpoint for `FAILED` rows, and
resubmission (`RESUBMITTED` exists in the audit vocabulary for that future flow).

## Database and verification

Flyway V8 adds the platform-admin role, V9 adds funds and county associations,
V10 adds applicants, applications, lookup values and transition records, and V14
replaces those with the insert-only audit trail plus the notification outbox and
delivery-attempt tables. The implementation retains the repository's existing UUID user ID
choice, rather than changing user IDs to the assessment's integer requirement.

Run the API/auth tests with:

```sh
./mvnw -Dtest=JpaAuditingTests,JwtSecurityTests,RoleTests test
```

Testcontainers requires Docker. Full application setup also requires all four
bootstrap environment values in addition to the database and JWT settings.
