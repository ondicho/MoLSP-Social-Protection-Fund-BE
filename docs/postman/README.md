# Postman API package

## Import and run

Import `Social-Assistance-Fund.postman_collection.json` and one environment:

- For this configured local Docker deployment, import the **ignored, private**
  `.env.postman.local.json` from the repository root. It contains the bootstrap
  administrator credentials and must never be committed or shared.
- For another deployment, import `Local.postman_environment.json`, set `baseUrl`
  and `adminEmail`, and enter `adminPassword` locally from that deployment's
  `PLATFORM_ADMIN_PASSWORD`. The distributed environment deliberately has no secrets.

Select the environment and run the entire collection in numeric folder order.
The ZIP contains the collection, secret-free environment template and this guide;
the private environment is deliberately excluded. Use a local Postman runner
or Newman, not a cloud runner, and do not sync/export populated credentials,
tokens or run reports to shared workspaces.

| Folder | Coverage |
|---|---|
| 00 - Health and API documentation | Health, readiness, liveness, OpenAPI JSON and Swagger UI |
| 01 - Authentication | Bootstrap admin login and initialization of a fresh demo run |
| 02 - Staff administration | Create all three staff roles, list/filter, fetch, update, deactivate/reactivate and role-specific login |
| 03 - Geography | Counties, sub-counties and wards; resolves seeded Baringo / Baringo Central IDs |
| 04 - Social funds | County/national creation, list/filter, fetch, versioned update, archive/unarchive |
| 05 - Public self-registration | Complete public form, submitted receipt without applicant PII |
| 06 - Assisted application drafts | Officer creation, list/search, fetch, versioned update and submission |
| 07 - Approver workflows | Review and approve; separate applications for rejection and return for correction |
| 08 - Validation and access boundaries | Expected 400/401/403/404/409 responses |

Every implemented controller method is represented. Unimplemented functionality
(applicant login, correction/resubmission, notifications, application deletion)
is not invented. Metrics and other Actuator endpoints are blocked by design;
only health probes are exposed.

## Automatic variables and assertions

The admin login request generates a fresh `runId`, unique synthetic staff emails,
a temporary staff password and current dates. Restart from this request for a
fresh full run. Each run creates persistent demo data; run only against a local
or disposable demonstration database. No cleanup deletes application records.
The extra demo administrator is deactivated; the bootstrap administrator is
never edited or disabled.

Staff logins capture separate `adminToken`, `officerToken` and `approverToken`.
Creation/read/update requests capture IDs and optimistic versions in environment
variables. Tokens expire after the configured TTL (default 15 minutes). To
continue a paused workflow, re-run the relevant login request; for the admin,
send the same login body without its initialization pre-request script so existing
workflow variables are preserved.

Applications send **only `subCountyId`**; the service derives the county. The
`countyId` variable is still used for geography navigation, county fund scope
and application search. Responses retain derived county information.
Optional list filters are present but disabled in Postman's Params tab;
enable the desired filters. Geography accepts sort fields `name`/`id`; other
lists use their documented entity fields and default to `createdAt`.

Assertions check HTTP statuses, wrapped API responses, request-ID propagation,
pagination inside `data`, geography discovery, and the expected workflow
results. Actuator and OpenAPI/Swagger return their native formats, not
`ResponseWrapper`.

The packaged collection was executed against the local Docker stack: all 56
requests and 187 assertions passed. Its routes were also compared with the
running OpenAPI document: all 25 documented API operations are represented.

For individual manual actions, run their prerequisites first: staff creation and
role login, geography discovery, then the application's creation/submission/review.
Approval, rejection and return-for-correction operate on different applications.
Read the current resource before a manual update to refresh its version.

## Local Newman run

With Docker running and the private local environment generated:

```sh
npm exec --yes --package=newman -- newman run \
  docs/postman/Social-Assistance-Fund.postman_collection.json \
  -e .env.postman.local.json \
  --reporter-cli-no-console
```

Do not pass passwords as command-line arguments, commit populated environments,
or export Newman JSON/HTML reports without protecting/redacting their contents.
Newman keeps captured tokens and demo credentials in memory unless explicitly
asked to export them; Postman retains the variables in your selected environment.

## Docker lifecycle

```sh
docker compose up --build -d --wait
docker compose ps
curl --fail http://localhost:8080/actuator/health/readiness
docker compose logs --tail=100 app
docker compose down
```

`down` retains database and log volumes. Do not remove volumes to fix an upgrade
or credential issue. Changing bootstrap environment values does not reset an
existing administrator. Changing database environment passwords does not rotate
accounts in an existing MySQL volume.
