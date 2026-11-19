# Social Assistance Fund

Frontend agent handoff: [Frontend implementation guide](docs/frontend-implementation-guide.md).

Java 21 / Spring Boot application with MySQL 8.4 and Flyway migrations.
This is an evolving implementation, not a complete assessment submission:
applicant correction/resubmission, email notifications and frontend are not
yet implemented.

## Docker deployment

Requires Docker Engine/Desktop and Docker Compose v2 or later.
The deployment files are `docker-compose.yml` and `Dockerfile`.

```sh
cp .env.example .env
chmod 600 .env
```

Edit `.env`: set different strong `DB_PASSWORD` and `DB_ROOT_PASSWORD` values,
set `JWT_SECRET` to the output of `openssl rand -base64 32`, and configure the
four `PLATFORM_ADMIN_*` values for the first administrator. Use a password of at
least 12 characters. Do not commit `.env`,
print resolved Compose configuration into shared logs, or include secrets in image
build arguments. Quote values containing `$` or `#` in `.env` using single quotes.

```sh
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/actuator/health/readiness
```

The app waits for authenticated MySQL readiness, applies migrations and validates
the JPA schema. The database is not published to the host. The app binds to host
loopback by default; change `APP_BIND_ADDRESS` only behind an HTTPS reverse proxy.
The image uses a non-root runtime user, read-only root filesystem, a temporary
`/tmp`, and writable named log volume. Do not mount Docker socket or host secrets.
Java build/test execution outside Docker requires Java 21+ and the Maven wrapper.
The Docker build packages without running tests: run checks separately before
deployment. Pin image digests and scan/update base images for production releases.

Configuration is documented in `.env.example`: image tags, ports, database/pool,
JWT, initial platform admin, phone policy, CORS, log rotation and health timings. Compose reads `.env`
automatically; plain Java/Maven execution does not load it automatically. Export
the required variables in your shell/IDE for local execution. Compose constructs
its internal JDBC URL; `DB_URL` is used when running the app outside Compose.
Security invariants such as disabled heap dumps are deliberately not exposed as
deployment toggles. In production, use a secret manager rather than a shared env file;
container environment values are visible to administrators with Docker access.

Database credentials initialize only a fresh MySQL volume. Editing `.env` does not
rotate existing database accounts: rotate them in MySQL and update/restart the app.

```sh
docker compose logs --tail=100 app
docker compose exec app sh -c 'tail -n 100 /app/logs/access.log'
docker compose exec app sh -c 'tail -n 100 /app/logs/error.log'
docker compose down
```

`down` retains data/log volumes. Back up the database before upgrades; never remove
volumes to resolve migration failures. The initial platform admin is seeded once
from environment values and can sign in at `/api/auth/login`; see
[authentication and bootstrap](docs/AUTHENTICATION.md).

The [Postman API package](docs/postman/README.md) contains a feature-organized
collection, a secret-free environment template and a ZIP for import. For the
configured local deployment, import the ignored `.env.postman.local.json`
environment instead; never share or commit that credential-bearing file.

## Access, application and error logs

| Stream | Contents |
|---|---|
| `access.log` | HTTP method, matched route template (never raw path), status, duration and correlation ID. Successful GET/HEAD health probes are suppressed; failed probes remain visible. Rejections use WARN and server failures ERROR. No query, IP, headers or body. Async/error redispatch may produce multiple entries for one request; requests rejected before route matching show `UNRESOLVED`. |
| `application.log` | Operational events (including WARN/ERROR), excluding access entries. |
| `error.log` | ERROR events only, also present in the application log. |
| Container stdout | All three streams, using Docker's bounded `local` logging driver. |

File streams share the PII-safe formatter and UTC timestamps; raw throwables are
suppressed. `X-Request-ID` links responses and all log streams. Files rotate daily
(UTC) or at `LOG_MAX_FILE_SIZE`, whichever occurs first; archives are gzip-compressed.
`LOG_MAX_HISTORY` limits retained daily periods and `LOG_TOTAL_SIZE_CAP` limits
archives **per stream**, excluding the active file. Defaults are 10 MB per file,
14 days and 200 MB per stream, plus active files. Cleanup occurs at rollover/start;
these are operational bounds, not guaranteed legal erasure deadlines.
Docker stdout uses separate `DOCKER_LOG_MAX_SIZE`/`DOCKER_LOG_MAX_FILES` bounds,
defaulting to 10 MB x 3 per container. MySQL container output uses those bounds too;
application masking does not apply to MySQL's own logs. Do not enable its general
query log in production. Docker driver changes require recreating containers.

Named volumes survive container recreation. Restrict host/Docker-admin access,
encrypt the underlying disk and configure remote collection/access/retention
according to the Ministry-approved policy. Do not run external `logrotate` against
files already managed by Logback. Set filesystem quotas/monitor disk usage: archive
caps are not whole-volume quotas. See [data/logging design](docs/DESIGN_NOTE.md).

Application validation logs include safe diagnostic metadata: invalid fund
selections record `field=programmeCodes`, a stable reason, and requested/matched/
active fund counts, not submitted slugs or applicant fields. General HTTP errors
record status/category and invalid-field count. Use `X-Request-ID` to correlate
these entries with the response. A fund list saved before demo cleanup is stale;
fetch the current list and create a real fund before applying if it is empty.

## Verification

```sh
./mvnw -Dtest=PhoneNumberNormalizerTests,CorrelationIdFilterTests,PiiMaskerTests,UserTests,AuthenticatedAuditorTests,JwtSecurityTests,GlobalExceptionHandlerTests,ResponseHandlerTests,ActuatorSecurityTests,RoleTests test
./mvnw -Dtest=JpaAuditingTests test
```

The MySQL integration check requires Docker and downloads `mysql:8.4`. Full test
execution also includes the scaffold context test, which needs external database
and JWT configuration. Assessment effort/time tracking and incomplete feature
inventory must be completed by the candidate before submission.

The Docker image build and an isolated Compose deployment were verified: MySQL and
the app became healthy, readiness returned HTTP 200, the app ran as non-root, and
the access-log entry carried the supplied request ID. MySQL migration/auditing
checks and log-stream separation/rotation checks pass.

The application seeds a provisional 47-county/333-sub-county YAML reference
catalogue and exact-name-matched wards on startup. See
[geography sources, API and limitations](docs/GEOGRAPHY.md) and the
[repository coding standards and structure](agent.md).

Phase 2 provides platform-admin staff/fund management and assisted/public
application intake with approver transitions, an immutable application audit
trail and an async transactional-outbox SMS notification flow (optional
TechVoyage gateway via `COMMS_*` env values; the app starts without them). See
[Phase 2 API and workflow notes](docs/PHASE_TWO.md#audit-trail-and-notifications).
