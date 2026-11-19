# User data and operational logging design

## Country-scoped phone numbers and user profiles

`PhoneNumberNormalizer` uses libphonenumber metadata, not prefix substitution alone.
`PHONE_DEFAULT_REGION=KE` interprets national numbers and
`PHONE_ALLOWED_COUNTRY_CODES=254` restricts accepted calling codes. Both are injected
from the environment. The default region's code must be allowed; invalid configuration
fails startup. For multiple countries use e.g. `254,256` with `KE` as the default;
send non-default-country numbers with an explicit `+` prefix. Shared calling codes
(such as +1) cover multiple territories, so a calling-code allowlist is not a
country/residency check.

| Input (Kenya defaults) | Stored E.164 value |
|---|---|
| `0712345678` | `+254712345678` |
| `254712345678` | `+254712345678` |
| `+254712345678` | `+254712345678` |

Formatting spaces, parentheses and hyphens are accepted. Missing, invalid,
overlong, extension-bearing and disallowed-country numbers are rejected without
echoing input. Valid-format numbers are not necessarily assigned or owned: an OTP
challenge and a verified-at timestamp are future requirements. Phone numbers are
not unique identifiers; shared household contacts are possible.

`UserProfileListener` normalizes and validates on JPA insert/update for every user
management path. Future registration/update services must also call the shared
normalizer before persistence to return the field-level `phoneNumber` HTTP 400
response early; no registration/update endpoints exist yet. Native SQL and bulk
updates bypass listeners and must not be used for ordinary user management.
V5 leaves historical phones NULL rather than inventing personal data; legacy users
must supply a valid phone on their next JPA update. The database enforces E.164
format for non-NULL values, not the environment-dependent country policy.

Middle name is nullable in both JPA and MySQL. The constructor skips NULL/blank
parts when building the full name; the listener turns blank middle names into NULL.

## Correlation, logging and access

The highest-priority servlet filter runs before authentication, attaches a validated
`X-Request-ID` (or generates a UUID), echoes it in the response and sets the MDC.
The same ID appears in response wrappers and console logs. MDC is restored in a
`finally` block and set again on async/error redispatch. Background executor tasks
need explicit MDC propagation; do not copy credentials/security contexts casually.
Incoming correlation IDs are untrusted metadata, never an authorisation decision.
Each dispatch logs only completion status and duration, not URLs/query strings.
Clients must not put personal data or secrets in correlation IDs.

The Logback console and rotating files include UTC timestamps, severity, logger and correlation ID.
Its message converter redacts common credential labels, bearer tokens, JWTs,
emails, labelled identifiers/names/addresses and long numeric identifiers, and
removes line breaks. Throwable rendering is suppressed because nested messages
can contain SQL values or credentials. Generic exception logs record the exception
class only. SQL/bind/extract and JDBC exception-detail logging are disabled.

Redaction is defence in depth, not a way to make arbitrary data safe. Never log
request/response bodies, auth headers, tokens, passwords, user objects, free-text
reasons, full names, phone/email contacts or ID/passport values. Unlabelled
alphanumeric passport numbers cannot be reliably detected by regex. Use internal
UUIDs and fixed event/reason codes instead. Keep log configuration unchanged in
production; additional appenders must apply the same masking and throwable policy.

Workflow and notification services do not exist yet. When implemented, emit
`workflow_rejected` with application UUID, actor UUID, action/state and a fixed
reason code; emit `notification_failed` with outbox/request UUID, provider code and
failure category. Never emit provider response bodies, destination addresses,
applicant identifiers or human-entered rejection reasons. Tie HTTP-triggered events
to the current MDC; background delivery must restore the originating correlation ID.
This logging is distinct from access-controlled domain audit records.

Restrict personal-data reads through role and ownership checks, return DTOs, encrypt
transport/storage/backups, and grant database/log access only to operational roles
with a business need. Existing JWT role checks alone are not complete user-management
authorisation. Audit actor UUIDs refer to authenticated principals; do not log JWTs.

## Retention and remaining hardening

The Ministry must approve a legal retention schedule for applications, identity
data, audit records, operational logs and backups before production. No legal
duration is assumed here. Soft deletion is not erasure: scheduled purge/anonymisation
must respect statutory retention/legal holds, remove expired backups on schedule
and preserve only the minimum required audit evidence. Apply a shorter, separately
approved TTL to restricted-access operational logs and audit log-access activity.

Time-box trade-offs: metadata validation instead of OTP verification; a console
and rotating-file masking safety net instead of a managed log pipeline; nullable legacy phones instead
of fabricated backfills. Next steps: implement verified phone/user-management
workflows with ownership controls; integrate safe workflow/outbox events; implement
approved retention, purge and monitored access policies.

County and sub-county lookup tables are seeded from a provisional historical census
catalogue; source differences, integer keys and idempotent seeding are documented in
[GEOGRAPHY.md](GEOGRAPHY.md). Location and village free-text support is intended but
not yet implemented; replacing it with an authoritative full hierarchy is technical
debt and a deviation from the assessment requirements.

Verification:

```sh
./mvnw -Dtest=PhoneNumberNormalizerTests,CorrelationIdFilterTests,PiiMaskerTests,JwtSecurityTests,GlobalExceptionHandlerTests test
./mvnw -Dtest=JpaAuditingTests test
```

The second command requires Docker and the `mysql:8.4` image. MySQL migrations,
schema validation, listeners, rollback and soft-delete checks pass. Compose startup,
non-root runtime, readiness and persisted access logs were verified. See the root
README for deployment and per-stream rotation/retention settings.
