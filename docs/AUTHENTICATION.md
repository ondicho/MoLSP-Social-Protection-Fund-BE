# Authentication foundation

`JwtProvider` combines access-token generation, validation and bearer authentication
in one `OncePerRequestFilter`. It runs only in the Spring Security filter chain.
Tokens contain the UUID user ID as their subject; each request reloads the account
and its single role, so disabling an account or changing its role takes effect immediately.
Email remains the username for `UserDetailsService` credential lookups.

Set `JWT_SECRET` to a Base64-encoded random key of at least 32 bytes, for example
generated locally using `openssl rand -base64 32`. No default signing secret is
provided. `JWT_ACCESS_EXPIRATION_MS` defaults to 900000 (15 minutes) and must be
positive. Use HTTPS and send tokens as `Authorization: Bearer <token>`; do not
store them in cookies with the current CSRF-disabled configuration.

Set `CORS_ALLOWED_ORIGINS` to a comma-separated list of exact frontend origins,
such as `http://localhost:5173`. Cross-origin access is denied by default.
Sessions are stateless. Authentication and authorisation failures return
the standard JSON response wrapper with Problem Details in `data`, with HTTP 401
and 403 respectively. Swagger and health
endpoints are public; all other endpoints require authentication.

## Actuator and CORS hardening

Heap dumps are disabled with `management.endpoint.heapdump.access=none`, excluded
from web exposure, and blocked by Spring Security. Only health is web-exposed;
all other Actuator endpoints are denied even to authenticated application users.
Endpoint-aware matchers also protect exposed endpoints when the management base
path changes. JMX endpoint exposure is disabled. Health details/components are
hidden; readiness includes the database. Metrics remain available internally but
are not exposed over HTTP. Do not broaden management access or exposure in
deployment overrides; restrict management traffic at the network layer too.

The CORS bean is explicitly injected into the security chain. Configure it using
these comma-separated environment variables:

| Variable | Default |
|---|---|
| `CORS_ALLOWED_ORIGINS` | Empty: no cross-origin access |
| `CORS_PATHS` | `/**` |
| `CORS_ALLOWED_METHODS` | `GET,POST,PUT,PATCH,DELETE,OPTIONS` |
| `CORS_ALLOWED_HEADERS` | `Authorization,Content-Type,X-Request-ID` |
| `CORS_EXPOSED_HEADERS` | `X-Request-ID` |
| `CORS_MAX_AGE` | `3600` seconds |

Use exact origins, e.g. `https://fund.example.go.ke`, and paths such as `/api/**`.
Wildcard origins cause startup to fail. Cookie credentials are intentionally
disabled for bearer-token authentication. CORS is a browser policy, not endpoint
authorisation; it does not prevent non-browser clients from making requests.

The `User` entity extends the shared `Base` mapped superclass. All future JPA
entities must also extend `Base`; enums and DTOs do not. Inherited fields include
the UUID ID, optimistic-lock version, creation/modification audit fields, and
soft-deletion fields. Spring Data's `AuditingEntityListener` is inherited and
enabled by `JpaAuditingConfig`. It sets timestamps and actor UUIDs on persist/update.
The actor comes from the authenticated `User` principal installed by `JwtProvider`
after token verification, never from unverified token claims or request payloads.
No extra user lookup is performed by the listener.

Writes require an enabled, authenticated user principal with an ID. Missing,
anonymous or unsupported principals cause the write/transaction to fail; no
`SYSTEM` fallback is used. Background jobs must establish an authenticated service
account context explicitly and clear it afterward. Initial account provisioning
must happen through a controlled bootstrap/SQL process before authenticated JPA
writes are possible. Bulk JPQL/native SQL and migrations bypass JPA listeners;
they must populate audit fields explicitly. Do not bind audit fields from API input.
Soft-deletion actor/time remain explicit operation fields, not automatic deletion.
`Base`'s soft-delete restriction prevents loading deleted accounts for JWT access.

Migration V4 adds inherited fields and the name columns previously missing from the
schema. Historical actor values are marked `LEGACY_UNKNOWN`, with no default for
new writes; unknown historical name parts are left empty rather than inferred.
The initial Flyway migration
creates `users`, `roles` and `user_roles` and seeds role names only. Configure the
MySQL datasource externally before starting the application.

Migration V2 replaces the join table with a required `users.role_id` foreign key,
so each user has exactly one existing role. Before upgrading an existing database,
ensure every user has exactly one assignment in `user_roles`. The migration rejects
multiple assignments or missing roles rather than choosing a role automatically.
MySQL DDL is not transactional; a failed migration requires inspecting and repairing
the partially applied schema before using Flyway repair and retrying.

`Role` is now an enum implementing the project's `enums.BaseEnum`, with stable
codes, display names and active flags. Supported roles are `PLATFORM_ADMIN`,
`DATA_ENTRY_OFFICER`, `APPROVER` and `APPLICANT`. Use `BaseEnum.sortAlphabetically(Role.class)` for
display-name ordering. Migration V3 converts `role_id` to a required `role_code`
string, preserving assignments and retaining the roles lookup table as a foreign-key
target. Migration V8 adds `PLATFORM_ADMIN` and updates the database role
constraint. Adding a role requires updating both the enum and database constraints/
lookup data.

The first platform admin is seeded idempotently at startup using
`PLATFORM_ADMIN_FIRST_NAME`, `PLATFORM_ADMIN_LAST_NAME`, `PLATFORM_ADMIN_EMAIL`,
and `PLATFORM_ADMIN_PASSWORD`. The password must be at least 12 characters and is
BCrypt-hashed. Bootstrap does not change an existing user's password or profile;
an email already used by a non-admin stops startup. The seeded admin signs in
through `POST /api/auth/login`; token responses are wrapped and contain no password
fields. Never log request credentials or bearer tokens.

`PLATFORM_ADMIN` can manage staff roles `PLATFORM_ADMIN`, `APPROVER`, and
`DATA_ENTRY_OFFICER` through `/api/admin/users`. Applicant accounts are deliberately
not created: applicants are application records created through assisted or public
intake. Staff creation requires an admin-provided temporary password which is
hashed and never returned. User deletion deactivates the account; at least one
enabled platform admin must remain. Users can sign in with their temporary password,
but password reset/rotation and login rate limiting are future hardening work.

Authorities are prefixed with `ROLE_`; application review routes use method security
and enforce ownership/state rules in the service layer. `generateAccessToken(UUID)`
is called only after the login service verifies the password. User UUIDs intentionally
follow the supplied model but differ from the practical test's integer-primary-key
requirement; resolve that deviation before submission.

The public self-registration audit actor is `PUBLIC_SELF_REGISTRATION`, a fixed
service marker used only in the scoped auditing context for the public form write.
All authenticated changes use the authenticated user's UUID. Public submissions
have no applicant login or continuation token; returned-for-correction forms cannot
currently be amended or resubmitted.

Run the focused authentication and user-model checks without a database:

```sh
./mvnw -Dtest=ActuatorSecurityTests,JwtSecurityTests,UserTests,RoleTests test
```

Run the auditing/listener and migration checks against MySQL with Docker running:

```sh
./mvnw -Dtest=JpaAuditingTests test
```

The auditor and affected non-database regression checks pass. MySQL migration,
schema validation, listener and rollback checks also pass against `mysql:8.4`.
The application Logback configuration is populated and tested; integration tests
use it directly. Docker Compose startup and database readiness were verified.

See [the user-data design note](DESIGN_NOTE.md) for environment-configured phone
normalization, nullable middle names, correlation IDs, PII-safe logging and retention.
