# API error responses

All responses built with `ResponseHandler`, including global exception and security
errors, use a generic `ResponseWrapper<T>` with `success`, `message`, `data`, `error`,
`timestamp` and `requestId`. Jackson handles JSON; Gson is not required.
Responses use `application/json`, not `application/problem+json`: errors contain
the Problem Details object inside `data` rather than as the top-level document.
Problem Details includes `type`, `title`, `status`, `detail`, request `instance`,
ISO 8601 UTC `timestamp` and `requestId`.
Validation failures also include `errors`, an object keyed by field/path with a list
of validation messages. No rejected values or implementation stack traces are
returned.

Example validation response:

```json
{
  "success": false,
  "message": "Bad Request",
  "error": "Request validation failed",
  "timestamp": "2026-11-17T18:00:00Z",
  "requestId": "example-request-1",
  "data": {
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "Request validation failed",
    "instance": "/api/applications",
    "timestamp": "2026-11-17T18:00:00Z",
    "errors": {
      "firstName": ["must not be blank"],
      "applicationDate": ["must be a valid date"]
    },
    "requestId": "example-request-1"
  }
}
```

Throw `ResourceNotFoundException` for missing resources and
`DuplicateResourceException` for duplicate resources. They map to HTTP 404 and 409.
Optimistic-lock and database-integrity conflicts also map to 409. `ApiProblemDetails`
is the shared factory for both MVC exception responses and Spring Security's 401/403
responses. `ResponseHandler` wraps both and returns `X-Request-ID` as a response
header. Incoming IDs may contain 1–100 ASCII letters, digits, dots, underscores or
hyphens; otherwise a UUID is generated and cached on the request. Controllers can
call `responseHandler.requestId(request)` when creating successful responses.
Use `success` and `created` for typed payloads. HTTP 204 responses must have no body
and should not use the wrapper.

Dates and timestamps in request/response DTOs should use Java time types such as
`LocalDate` and `Instant`; Spring's JSON configuration serializes these as ISO 8601.
Do not expose persistence entities as API responses.

Run the focused checks with:

```sh
./mvnw -Dtest=ResponseHandlerTests,GlobalExceptionHandlerTests,JwtSecurityTests test
```
