# Frontend implementation handoff

Contract snapshot: 19 November 2026. This is a standalone implementation brief
for the frontend coding agent, based on the current controllers, DTOs, services
and security configuration. It describes **implemented behavior**, not a
production-complete target system.

## 1. Assignment and boundaries

Implement a minimal, accessible Social Assistance Fund frontend using **Vue.js
with Pebble templates**, as required by the assessment. Deliver staff login,
role-specific navigation, staff administration, fund management, assisted
application intake, application search/details and approver actions. Build the
public application experience using the public geography and fund catalogues.

There is currently no Vue application, frontend package manifest, Pebble page
controller, template or Pebble dependency configured. Establish that integration
deliberately; do not assume an existing frontend scaffold. A practical structure
is a Pebble-rendered HTML shell with Vue mounting into a dedicated element,
feature modules, a shared API client and a centralized permission map. Avoid
Vue/Pebble interpolation conflicts, for example by using Vue templates compiled
into the asset bundle. Do not scatter API requests through templates.

Read `agent.md` before changing the repository. Keep backend controllers thin,
preserve service-owned validation and authorization, and never broaden API
permissions merely to make a screen work. Backend additions listed below require
an explicit implementation decision; do not silently invent or call new routes.
No database reset, demo seeding, account/password changes or Postman write runs
are part of this frontend task.

### Critical integration gaps

| Gap in current backend | Impact and required frontend behavior |
|---|---|
| No general lookup API | Gender/status/fund enums and current marital-status values can be represented as typed constants below; configurable marital values need an eventual read endpoint. No `/lookups` endpoint exists today. |
| Application response has no creator ID | Officers are restricted to their own records server-side, but the client cannot independently compare ownership. Rely on server-scoped lists and service authorization; handle 403. Do not infer ownership from applicant identity. |
| No archived-fund listing | An archived fund disappears from list/detail. There is a toggle by known ID, not an archive browser or explicit restore endpoint. Retain the just-archived ID in current UI state for undo; do not promise recovery after reload without backend support. |
| UI routes/static resources are not yet permitted in Spring Security | A same-origin public shell and required assets need an explicit, narrowly scoped security configuration change. Current `anyRequest().authenticated()` will otherwise protect them. API role restrictions must remain intact. |

Render clear loading, empty and blocked states. An empty fund list is a real
state, especially after demo cleanup, not a reason to synthesize options. A
frontend-only build must document remaining gaps accurately. Geography and fund
listing are public; fund detail and mutations remain admin-only.

## 2. Roles and permission model

Users have **one** role. Compare enum names exactly, without a `ROLE_` prefix in
frontend state. `ROLE_` is an internal Spring authority convention.

| Capability | Anonymous/public | PLATFORM_ADMIN | DATA_ENTRY_OFFICER | APPROVER |
|---|---|---|---|---|
| Staff login | Yes | Yes | Yes | Yes |
| Submit public completed form | Yes | Public route remains accessible | Public route remains accessible | Public route remains accessible |
| Geography lookups | Yes | Yes | Yes | Yes |
| Manage/list/detail staff | No | Yes | No | No |
| List funds | Yes | Yes | Yes | Yes |
| Manage/detail funds | No | Yes | No | No |
| List/detail applications | No | All | Own only | All |
| Create assisted draft | No | No | Yes | No |
| Edit/submit assisted draft | No | No | Creator, DRAFT only | No |
| Start review | No | No | No | SUBMITTED only |
| Approve/reject/return | No | No | No | UNDER_REVIEW only |
| Amend/resubmit returned record | No | No | No | No |

`APPLICANT` exists in the backend enum but is **not a supported login/account
workflow**. Applicants are records without credentials. Staff management rejects
creation or assignment of `APPLICANT`. Do not create an applicant portal login,
applicant dashboard or public application-status lookup.

There is no superuser bypass: platform admins cannot perform officer or approver
actions. Client route guards and hidden buttons improve UX only; the backend
remains authoritative. Treat unexpected/unknown roles as unsupported, not admin.

## 3. Complete endpoint inventory

Base URL in local Docker: `http://localhost:8080`. Use an environment-configured
API base URL, never a secret-bearing backend `.env` imported into browser assets.
All `{id}` values below are UUID strings unless explicitly described as geography
integers. All business API success/error responses use the wrapper in section 4.

### Public endpoints

| Method | Path | Input | Success data |
|---|---|---|---|
| POST | `/api/auth/login` | LoginRequest | LoginResponse, 200 |
| POST | `/api/applications/self-registration` | Complete FormRequest | `{applicationId, status: "SUBMITTED"}`, 201 |
| GET | `/actuator/health` | None | Native health JSON |
| GET | `/actuator/health/readiness` | None | Native health JSON |
| GET | `/actuator/health/liveness` | None | Native health JSON |
| GET | `/v3/api-docs` | None | OpenAPI document |
| GET | `/swagger-ui.html`, `/swagger-ui/index.html` | None | Swagger UI/redirect |

OpenAPI/Swagger resource subpaths are public. Other Actuator routes, including
metrics, are denied; do not implement a metrics dashboard using them. Public does
not mean an expired bearer token is ignored: the JWT filter processes supplied
Authorization headers before route authorization. Omit Authorization for login
and all public catalogue/registration calls, even when a stale staff session exists.

### Public read-only catalogues

| Method | Path | Data |
|---|---|---|
| GET | `/api/geography/counties` | Page of County |
| GET | `/api/geography/counties/{countyId}/sub-counties` | Page of SubCounty |
| GET | `/api/geography/counties/{countyId}/sub-counties/{subCountyId}/wards` | Page of Ward |
| GET | `/api/social-funds` | Page of FundResponse; optional `search` and `type` filters |

Geography IDs are integers. All three endpoints take the common pagination
parameters. For geography, use `sortBy=name` or `id`; default natural sort is
name, descending. These exact GET routes require no authentication and are also
available to all staff roles. Do not send an Authorization header for catalogue
calls. Fund listing returns all non-archived funds, not only currently open funds.
Fund detail by ID remains admin-only; use list DTOs for public/assisted selection.

### PLATFORM_ADMIN only

| Method | Path | Input / result |
|---|---|---|
| GET | `/api/admin/users` | Page of UserResponse |
| GET | `/api/admin/users/{id}` | UserResponse |
| POST | `/api/admin/users` | CreateUserRequest -> UserResponse, 201 |
| PUT | `/api/admin/users/{id}` | UpdateUserRequest -> UserResponse |
| PATCH | `/api/admin/users/{id}/enabled?enabled=true` | UserResponse; boolean query parameter is required, false deactivates |
| DELETE | `/api/admin/users/{id}` | Deactivates, returns UserResponse; does not delete |
| GET | `/api/social-funds/{id}` | Active FundResponse |
| POST | `/api/social-funds` | CreateFundRequest -> FundResponse, 201 |
| PUT | `/api/social-funds/{id}` | UpdateFundRequest -> FundResponse |
| PATCH | `/api/social-funds/{id}/archive` | No body -> `{id, archived: boolean}`; toggles both ways |

Staff filters: `search`, `role`, `enabled`, all optional. Sort allowlist:
`createdAt`, `updatedAt`, `email`, `firstName`, `middleName`, `lastName`, `role`,
`enabled`.

Fund filters: `search` (name/slug), `type`, both optional. Sort allowlist:
`createdAt`, `updatedAt`, `name`, `slug`, `type`, `openDate`, `closeDate`.
No DELETE fund route, `isArchived` filter, slug-based GET or public fund-detail
route exists. The fund list is public as documented above.

### Application RBAC endpoints

| Method | Path | Roles and rules | Input / result |
|---|---|---|---|
| GET | `/api/applications` | Admin/approver all; officer own only | Page of ApplicationResponse |
| GET | `/api/applications/{id}` | Same visibility rules | ApplicationResponse |
| POST | `/api/applications` | Officer only | Partial FormRequest -> DRAFT ApplicationResponse, 201 |
| PUT | `/api/applications/{id}` | Creating officer; DRAFT only | `{version, form: FormRequest}` -> ApplicationResponse |
| POST | `/api/applications/{id}/submit` | Creating officer; DRAFT only | `{version}` -> SUBMITTED ApplicationResponse |
| POST | `/api/applications/{id}/review` | Approver; SUBMITTED only | `{version}` -> UNDER_REVIEW ApplicationResponse |
| POST | `/api/applications/{id}/approve` | Approver; UNDER_REVIEW only | ApprovalRequest -> APPROVED ApplicationResponse |
| POST | `/api/applications/{id}/reject` | Approver; UNDER_REVIEW only | `{version, reason}` -> REJECTED ApplicationResponse |
| POST | `/api/applications/{id}/return-for-correction` | Approver; UNDER_REVIEW only | `{version, reason}` -> RETURNED_FOR_CORRECTION ApplicationResponse |
| GET | `/api/applications/{id}/history` | Same visibility as detail | `pageNo`, `pageSize`, `sortDirection` (default `asc`) -> Page of AuditEventResponse |
| GET | `/api/applications/{id}/actions` | Same visibility as detail | AvailableActionsResponse for the **current caller** |
| GET | `/api/applications/{id}/notifications` | Same visibility as detail | `pageNo`, `pageSize`, `sortDirection` (default `asc`) -> Page of NotificationResponse |

Application list filters, all optional:
`search`, `status`, `applicationDateFrom`, `applicationDateTo`, `programme`,
`countyId`, `subCountyId`, `location`, `subLocation`, `village`.
Search matches first/last name or identity number, not middle name. Date filters
are inclusive application-date bounds; from must not exceed to. `programme` is
an **exact social fund slug**, not a fund UUID or legacy code. Geography text
filters are case-insensitive exact matches. Sort allowlist: `createdAt`,
`updatedAt`, `applicationDate`, `status`.

## 4. Shared client contracts

Use typed DTOs and a single HTTP client responsible for authorization, correlation,
response unwrapping and normalized errors. Example TypeScript contracts:

```ts
type Role = 'PLATFORM_ADMIN' | 'DATA_ENTRY_OFFICER' | 'APPROVER' | 'APPLICANT';
type ApplicationStatus =
  | 'DRAFT' | 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED'
  | 'REJECTED' | 'RETURNED_FOR_CORRECTION';
type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'UNDISCLOSED';
type FundType = 'NATIONAL' | 'COUNTY';
type FundCoverage = 'OPEN' | 'PLWD' | 'WOMEN' | 'YOUTH';

interface ResponseWrapper<T> {
  success: boolean;
  message: string;
  data: T | null;
  error?: string | null;
  timestamp: string;
  requestId: string;
}
interface PageData<T> {
  content: T[];
  pageNo: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
interface ApiProblem {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  timestamp: string;
  requestId: string;
  errors?: Record<string, string[]>;
}
interface County { id: number; name: string }
interface SubCounty {
  subCountyId: number;
  subCountyName: string;
  countyName: string;
}
interface Ward {
  wardId: number;
  wardName: string;
  subCountyName: string;
  countyName: string;
}
```

Lists are `ResponseWrapper<PageData<T>>`, never a root-level page. Single values
are directly under `data`. Errors generally use `ResponseWrapper<ApiProblem>`.
Read errors defensively because network/proxy failures might not be JSON. Null
properties may be present or omitted. Render strings as text, never raw HTML.

Pagination parameters: `pageNo=0`, `pageSize=2`, `sortBy=createdAt`,
`sortDirection=desc`; size range 1..100. Use the geography-specific sort described
above. Reset page to 0 when filters change. Do not client-filter a single server
page and present it as the full result set. Dropdowns must paginate too: request
up to 100 and continue until `last`, especially where there are more options than
the default two. Cancel/ignore stale cascading lookup and search responses.

Send a new `X-Request-ID` per user HTTP operation, e.g. `crypto.randomUUID()`.
Preserve the server's response ID for support/error presentation. Never log
request bodies, bearer tokens, identity numbers, phone numbers or full error
objects containing applicant input to browser analytics/console.

| HTTP result | UI handling |
|---|---|
| 400 | Show validation message; attach `data.errors[field]` where available; service business validation can be only a general detail |
| 401 on login | Invalid credentials/error message; no retry loop |
| 401 on protected request | Clear session, require login, preserve only a safe return route; do not automatically replay writes |
| 403 | Access-denied view/message, not an automatic logout |
| 404 | Resource missing/unavailable; refresh relevant list |
| 409 | Conflict: stale version, duplicate identity/email/phone or invalid workflow; show server detail and offer refetch |
| 5xx/network | Recoverable error view with request ID if available; explicit retry, never claim success |

## 5. Authentication and session UX

Login input:

```json
{"email":"staff@example.test","password":"<entered by user>"}
```

`data` on success:

```ts
interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresInMs: number;
  user: { id: string; firstName: string; lastName: string; email: string; role: Role };
}
```

Send `Authorization: Bearer <accessToken>` to protected API routes. The token
subject is the user UUID; it does not carry the frontend role/profile. Use the
login response's `user` for the UI, never decode a role that is not in the JWT.
Backend permissions are resolved from the account on every request.

Default expiry is 900000 ms (15 minutes); use returned `expiresInMs`. Keep tokens
and profile in memory by default, require login after reload, and clear them on
logout. There is no `/me`, refresh, logout/revocation, registration-for-staff,
password-reset or change-own-password endpoint. Logout is local clearing, not
server revocation. Do not put credentials/tokens in URLs or localStorage, or
promise persistent sessions without an explicitly approved design.

Landing navigation:
admin -> Staff / Funds / Applications;
officer -> My applications / New assisted draft;
approver -> Applications / Review queue.
Public -> informational landing / Apply / Staff sign-in.
Any role-aware dashboard is navigation and existing list data, not invented
aggregate statistics.

## 6. Staff management contracts

CreateUserRequest:

```ts
interface CreateUserRequest {
  firstName: string; middleName?: string | null; lastName: string;
  email: string; phoneNumber: string;
  role: 'PLATFORM_ADMIN' | 'APPROVER' | 'DATA_ENTRY_OFFICER';
  temporaryPassword: string;
}
interface UpdateUserRequest {
  firstName: string; middleName?: string | null; lastName: string;
  email: string; phoneNumber: string;
  role: 'PLATFORM_ADMIN' | 'APPROVER' | 'DATA_ENTRY_OFFICER';
  version: number;
  newPassword?: string;
}
interface UserResponse {
  id: string; firstName: string; middleName?: string | null; lastName: string;
  email: string; phoneNumber?: string | null; role: Role; enabled: boolean;
  version: number; createdAt: string; updatedAt: string;
}
```

Names: first/last required, each max 200; optional middle max 200; combined
full name max 200. Email required, valid, max 255 and unique. Phone required for
staff writes, max 16 input characters, normalized to E.164, globally unique
including deactivated/archived accounts. Default phone region KE; default allowed
calling code 254. Do not hardcode a more permissive policy than the deployment.
The bootstrap admin can initially have no phone; prompt for one if editing it.
Passwords: 12..72 characters for create/new password, also at most 72 UTF-8 bytes.
Omit `newPassword` entirely when unchanged; do not send an empty string.

Show enabled state and separate enable/deactivate actions with confirmation.
The last enabled platform admin cannot be demoted or deactivated. Never show
password hashes. There is no email delivery or forced-first-login reset workflow:
do not claim a temporary password was emailed or must be changed on first login.

## 7. Social fund management contracts

```ts
interface CreateFundRequest {
  name: string; type: FundType; coverage: FundCoverage;
  openDate: string; closeDate: string; minAmount: number; maxAmount: number;
  countyIds?: number[];
}
interface UpdateFundRequest extends CreateFundRequest { version: number }
interface FundResponse {
  id: string; name: string; slug: string; type: FundType; coverage: FundCoverage;
  openDate: string; closeDate: string; minAmount: number; maxAmount: number;
  counties: County[]; version: number; createdAt: string; updatedAt: string;
}
```

Name required/max 200. Open/close required ISO dates, **open strictly before
close**. Both amounts required/nonnegative, min <= max; schema uses decimal
precision 19, scale 2, currently assumed KES. Use a decimal-safe form strategy
and display two decimal places; avoid arithmetic that silently rounds monetary
values. County type requires at least one valid integer county ID; national type
requires no county assignments. Clear county selections when switching to
national. No duplicates; max 47 IDs.

Write `countyIds`, render `counties` DTOs. When opening edit, derive IDs from
`fund.counties.map(c => c.id)`. Do not write response DTOs back wholesale.
Slugs are generated from name and dates by the backend and may change on update;
display read-only. Slug references in applications cascade when funds are renamed.
Refresh cached fund options after edits; do not store stale slugs indefinitely.
Archive is a toggle, not idempotent DELETE: do not automatically retry it on an
ambiguous network failure. Archive response has no updated version; refetch
after restoring before a further edit.

## 8. Application intake contracts and validation

```ts
interface FormRequest {
  firstName?: string | null; middleName?: string | null; lastName?: string | null;
  gender?: Gender | null;
  dateOfBirth?: string | null; age?: number | null;
  maritalStatus?: string | null; identityNumber?: string | null;
  subCountyId?: number | null;
  location?: string | null; subLocation?: string | null; village?: string | null;
  postalAddress?: string | null; physicalAddress?: string | null;
  telephoneContacts?: string[] | null;
  applicationDate?: string | null;
  programmeCodes?: string[] | null;
  otherProgramme?: string | null;
  applicantSignedDate?: string | null;
}
interface ApplicationResponse {
  id: string; status: ApplicationStatus; source: 'ASSISTED' | 'SELF_SERVICE';
  firstName: string | null; middleName: string | null; lastName: string | null;
  gender: Gender | null; dateOfBirth: string | null; age: number | null;
  maritalStatus: string | null; identityNumber: string | null;
  countyId: number | null; subCountyId: number | null;
  location: string | null; subLocation: string | null; village: string | null;
  postalAddress: string | null; physicalAddress: string | null;
  telephoneContacts: string[];
  applicationDate: string | null; programmeCodes: string[]; otherProgramme: string | null;
  applicantSignedDate: string | null;
  officialName: string | null; officialDesignation: string | null;
  officialSignatureReference: string | null; officialSignedDate: string | null;
  decisionReason: string | null;
  version: number; createdAt: string; updatedAt: string;
}
```

Missing/null response values must render safely. Application responses currently
retain county/sub-county IDs and fund slugs, not nested fund or geography DTOs.
The applicant's own UUID is not exposed here.

### Complete-form requirements

Public registration and draft submission require first name, last name, gender,
date of birth **or** age, marital status, identity number, subCountyId, location,
subLocation, village, postalAddress, physicalAddress, at least one phone,
applicationDate, applicantSignedDate and at least one fund slug.
Middle name and otherProgramme are optional. Assisted drafts allow missing
mandatory fields, but supplied fields are still validated.

| Field | Constraints and UI behavior |
|---|---|
| Names | Each max 200; separate first/middle/last inputs |
| Date of birth | ISO date, not future; authoritative for calculated age |
| Age | Integer 0..120 if supplied; fallback when DOB absent. Prefer sending DOB with age omitted rather than contradictory values |
| Gender | Exact enum name from section 4 |
| Marital status | Current codes: SINGLE, MARRIED, DIVORCED, WIDOWED, SEPARATED, UNKNOWN; configurable in DB, no lookup endpoint yet |
| Identity | Alphanumeric only, 1..20, unique; normalized uppercase by service; do not use numeric inputs |
| subCountyId | Positive integer referencing a seeded sub-county; **no countyId in write body** |
| Location/sub-location/village | Text, each max 150; authoritative lower-level lookups do not exist |
| Postal/physical address | Each max 500 |
| Telephone contacts | One or more for complete form; each supplied string nonblank/max 64, then validated/normalized by backend |
| programmeCodes | Nonblank strings/max 250 each; actual existing, non-archived fund slugs, case-preserved and trimmed; never OVC/ELDERLY legacy codes |
| otherProgramme | Optional text/max 200, no special OTHER-code requirement |
| Signed dates | Applicant signed date >= applicationDate; official signed date >= both |

County may be a navigation-only dropdown. Changing it clears sub-county and
downstream selections; send only the final `subCountyId`. Ward lookup exists but
there is **no wardId field in the application DTO**. Do not add a required ward
field or submit one and assume it is saved. Ward coverage is incomplete due to
documented exact-name matching gaps; an empty ward list does not invalidate a
valid sub-county. Location/sub-location/village remain editable text.

Display fund **names**, store/send their **slugs**. A zero-fund catalogue must show
"No funds available" and disable completed submission. Current validation checks
existence/non-archived status, not opening window, county scope, amount,
vulnerability or income eligibility. Do not claim automated eligibility or add
unapproved frontend-only business rules. Application requests have no fund-amount
or income fields.

Public success is only `{applicationId, status}`. Show a receipt/reference and
clear sensitive form state; do not attempt the authenticated detail endpoint.
There is no public receipt lookup or continuation token. Do not promise email/SMS.
Disable duplicate sends while pending; there is no idempotency-key API. On an
ambiguous timeout, explain uncertainty rather than automatically resubmitting.

Draft PUT is a **full form replacement**, not a field patch. Maintain the complete
editable form and send `{version, form}`. Omitted values can clear existing data.
On successful writes, replace local state with the server response, including
its version and normalized contacts. Do not silently persist PII in localStorage
or service-worker caches; clear form state on logout/navigation as appropriate.

## 9. Workflow, concurrency and action dialogs

| Current state | Action | Role/ownership | Next state |
|---|---|---|---|
| DRAFT | Edit | Creating officer | DRAFT |
| DRAFT | Submit complete form | Creating officer | SUBMITTED |
| SUBMITTED | Start review | Approver | UNDER_REVIEW |
| UNDER_REVIEW | Approve | Approver; complete official-use fields | APPROVED |
| UNDER_REVIEW | Reject | Approver; reason required | REJECTED |
| UNDER_REVIEW | Return for correction | Approver; reason required | RETURNED_FOR_CORRECTION |

SUBMITTED/UNDER_REVIEW are read-only to officers. APPROVED/REJECTED are terminal
for normal edits. RETURNED_FOR_CORRECTION is also locked in this version: **no
amendment/resubmission endpoint exists**. Show an explicit limitation, not a
nonfunctional "Resubmit" button.

Approval body:

```json
{
  "version": 2,
  "officialName": "Reviewing officer",
  "officialDesignation": "Approver",
  "officialSignatureReference": "OFFICE-REFERENCE",
  "officialSignedDate": "2026-11-19"
}
```

These are entered text/date fields, not file uploads or digital-signature
verification. Each official text field is required/max 200. Reject/return reasons
are required/nonblank/max 1000. Other actions use only `{version}`.

### Server-driven actions, history and notifications

Drive workflow buttons from `GET /api/applications/{id}/actions` rather than
re-implementing the matrix above. Render one button per returned action; show
none when `actions` is empty (terminal states, admins, non-owners). Use the
returned `version` in the request body.

```json
{
  "applicationId": "…", "status": "UNDER_REVIEW", "version": 2,
  "actions": [
    {"action": "APPROVE", "method": "POST", "path": "/api/applications/…/approve",
     "nextStatus": "APPROVED",
     "requiredFields": ["version", "officialName", "officialDesignation",
                        "officialSignatureReference", "officialSignedDate"]},
    {"action": "REJECT", "method": "POST", "path": "/api/applications/…/reject",
     "nextStatus": "REJECTED", "requiredFields": ["version", "reason"]},
    {"action": "RETURN_FOR_CORRECTION", "method": "POST",
     "path": "/api/applications/…/return-for-correction",
     "nextStatus": "RETURNED_FOR_CORRECTION", "requiredFields": ["version", "reason"]}
  ]
}
```

Possible `action` values: `UPDATE_DRAFT`, `SUBMIT`, `START_REVIEW`, `APPROVE`,
`REJECT`, `RETURN_FOR_CORRECTION`. Still map each to a typed dialog; do not build
requests from arbitrary server paths without an allowlist.

Audit timeline (`history`, paged) — AuditEventResponse:

```json
{
  "id": "…", "sequenceNo": 41, "eventType": "APPROVED",
  "actorId": "…", "actorName": "Jane Approver", "actorRole": "APPROVER",
  "occurredAt": "2026-11-19T10:15:00Z",
  "fromStatus": "UNDER_REVIEW", "toStatus": "APPROVED", "reason": null,
  "changes": [{"field": "officialName", "before": null, "after": "Jane Approver"}],
  "requestId": "…"
}
```

`eventType`: `CREATED`, `UPDATED`, `SUBMITTED`, `RESUBMITTED` (reserved),
`REVIEW_STARTED`, `APPROVED`, `REJECTED`, `RETURNED_FOR_CORRECTION`. `fromStatus`
is null for creation; `changes` may be empty or null. Public self-registration
shows `actorId: "PUBLIC_SELF_REGISTRATION"`, `actorName: "Public self-registration"`,
`actorRole: "PUBLIC"`. `"[REDACTED]"` values mean a sensitive field changed; show
"changed" without values.

Notification lifecycle (`notifications`, paged) — NotificationResponse:

```json
{
  "id": "…", "auditEventId": "…", "eventType": "APPROVED", "channel": "SMS",
  "recipient": "********5678", "subject": "Application approved", "body": "Dear …",
  "status": "RETRY_SCHEDULED", "attempts": 2, "maxAttempts": 8,
  "nextAttemptAt": "2026-11-19T10:17:00Z", "lastError": "PROVIDER_HTTP_503",
  "providerReference": null, "requestId": "…",
  "createdAt": "…", "updatedAt": "…", "sentAt": null,
  "deliveryAttempts": [
    {"attemptNo": 1, "startedAt": "…", "finishedAt": "…",
     "outcome": "RETRYABLE_FAILURE", "errorCategory": "PROVIDER_HTTP_503",
     "providerReference": null}
  ]
}
```

`eventType`: `APPLICATION_RECEIVED`, `RETURNED_FOR_CORRECTION`, `APPROVED`,
`REJECTED`. `status`: `PENDING`, `SENDING`, `RETRY_SCHEDULED`, `SENT`, `FAILED`.
`lastError: "PROVIDER_NOT_CONFIGURED"` means the SMS provider is not configured
and the message is queued. Delivery is asynchronous: a workflow response never
waits for SMS, so show a "notification queued" state and refresh this panel
(e.g. poll every 10–15 s while any item is `PENDING`/`SENDING`/`RETRY_SCHEDULED`,
stopping when the panel closes). Never show the full phone number or treat a
notification failure as a failed decision. There is no re-send endpoint.

Fetch current detail before acting. Send the **returned version**, never a
locally guessed increment. On 409, keep unsaved user input only in memory,
explain conflict, refetch and require explicit confirmation before retrying.
Do not treat every 409 as a stale version: it may be invalid state or duplicate
data. Fund/staff updates follow the same version rule; staff enable/disable and
fund archive endpoints currently do not take a version.

## 10. Screen/component implementation map

| Screen/feature | Responsibilities |
|---|---|
| Public landing | Clear fund purpose and staff sign-in; application entry visibly blocked if required catalogues unavailable |
| Staff login | Accessible email/password, pending/error state, session expiry and role landing |
| Staff list/editor | Paginated optional filters, role selector limited to managed roles, create/update, password visibility toggle, enable/deactivate confirmation |
| Fund list/editor | Paginated search/type filter, dates/decimal validation, county multiselect, read-only slug, archive confirmation and current-session undo |
| Application list | Role-specific title, all supported filters, server pagination, status badges, URL-safe filter state without applicant PII |
| Application form | Shared public/assisted sections, multiple phones/funds, dependent geography, field/general errors; public complete submit vs officer save draft/submit |
| Application detail | Read-only personal/contact/programme data, official-use section, reason and status; workflow buttons from `/actions`; audit timeline from `/history`; notification lifecycle panel from `/notifications` |
| Approver queue | Existing list endpoint with status filter, no invented queue API; detail review and decision dialogs |
| Shared UI | Page shell, role navigation, accessible forms/dialogs, pagination, empty/error states, request-ID support message |

Use semantic labels and fieldsets, keyboard-accessible multiselects/dialogs,
focus the first invalid field, announce async errors and status changes, and
provide textual status labels rather than color alone. Mask PII in overview
tables where appropriate; keep full data only where necessary for the workflow.
Render a useful responsive layout rather than exposing raw JSON.

## 11. Deployment, security and development fixtures

Backend: Java 21 / Spring Boot; Docker app on loopback port 8080. OpenAPI at
`/v3/api-docs` and Swagger at `/swagger-ui/index.html` are the live schema reference.
If the backend contract changes, update this guide and frontend types together.

A separate Vue dev origin (commonly `http://localhost:5173`) must be explicitly
allowed by `CORS_ALLOWED_ORIGINS`, or use an intentional development proxy.
CORS allows Authorization, Content-Type and X-Request-ID and exposes
X-Request-ID; credentials/cookies are disabled. The backend's `.env`, JWT signing
key, database credentials and bootstrap password must never enter frontend build
variables, HTML, git, screenshots, telemetry or shared Postman exports.

Existing Postman files in `docs/postman/` demonstrate API shapes but write
requests create persistent data. `Optional-Demo-Setup.postman_collection.json`
is opt-in only. `.env.postman.local.json` is private, ignored and contains local
credentials; do not copy it into assets or the handoff. Its old demo variables
were cleared after cleanup. Startup seeds reference geography, lookup values
and one configured platform admin, **not** demo staff, funds or applications.
Frontend startup must not create those either.

Use isolated mocks for frontend component tests and disposable test databases
for integration; never change the developer's accounts or repopulate demo data
without permission. Use the public read catalogues without staff tokens; do not
disable auth or use admin tokens for protected actions from other roles.

## 12. Acceptance checklist and handoff output

1. Establish Vue/Pebble build, mounting, asset and page routing integration;
   document commands and explicitly approved security changes.
2. Implement centralized typed API/session/error handling and role guards.
3. Implement all available role-specific screens and state/ownership restrictions
   using `/actions`, `/history` and `/notifications`, without pretending a
   resubmission API exists.
4. Verify public requests omit stale Authorization; protected requests include
   bearer token and X-Request-ID; 401/403/409 behavior is correct.
5. Test wrapped single/page/error shapes, nullable draft data, geography pagination,
   stale cascade responses, multiple phone/fund inputs and zero-fund state.
6. Test the complete transition/action matrix and that admins cannot approve or
   create assisted drafts, officers cannot edit submitted/returned records, and
   approvers cannot edit applicant data.
7. Test staff phone/email conflicts, last-admin protection, optional password
   update omission, national/county fund rules and county DTO-to-ID edit mapping.
8. Test latest-version updates, explicit conflict handling, duplicate-submit
   prevention, server normalization and no silent retries of archive toggles.
9. Verify no PII/credentials in browser logs, persistent storage, build artifacts
   or fixtures; no demo writes on startup.
10. Deliver working source, directly related documentation, focused tests and a
    concise list of backend blockers/limitations. Clearly separate mocked
    experiences from real integrated functionality.

Verify anonymous geography and fund listing work end-to-end and protected fund
detail/mutations remain admin-only. Do not
claim correction/resubmission, notifications, attachments, public tracking,
password recovery, server logout or audit-history browsing are available.
