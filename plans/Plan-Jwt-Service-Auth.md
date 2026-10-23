# Plan: Verify JWT in each service

## Brief Description

Every service will reject `/api/v1` calls that do not carry a valid JWT issued by user-service. `POST /api/v1/auth/login` stays public. The check lives in the HTTP layer. Use cases, messaging consumers, and scheduled jobs stay unchanged.

Swagger UI on each service gets an **Authorize** button. **Try it out** sends `Authorization: Bearer` with the raw login token.

This is authentication only. The token already has `groups` (`ADMIN`, `INSTRUCTOR`, `STUDENT`) and `sub` (user id). Role and ownership rules are a later task.

## Implementation Strategy

user-service signs with `smallrye.jwt.sign.key.location=privateKey.pem` (RS256). The claims are issuer `identity-service`, subject = user id, `groups` = the role name, expiry 8 hours. `quarkus-smallrye-jwt` is already on every service through `dpp.quarkus-service`. Nothing verifies the token today.

1. Derive a public key from the existing private key and put it in `dpp-commons` so every service sees it on the classpath:

   ```shell
   openssl pkey -in user-service/src/main/resources/privateKey.pem -pubout -out dpp-commons/src/main/resources/publicKey.pem
   ```

   Root `.gitignore` ignores `*.pem`. Add an exception for `dpp-commons/src/main/resources/publicKey.pem`. The private key stays ignored. A new private key requires a new public key and a rebuild of every service.

2. Add the same configuration to each service's `application.properties`:

   ```properties
   mp.jwt.verify.publickey.location=publicKey.pem
   mp.jwt.verify.issuer=identity-service
   quarkus.oidc.tenant-enabled=false

   quarkus.http.auth.permission.public-meta.paths=/q/*
   quarkus.http.auth.permission.public-meta.policy=permit

   quarkus.http.auth.permission.public-login.paths=/api/v1/auth/login
   quarkus.http.auth.permission.public-login.methods=POST
   quarkus.http.auth.permission.public-login.policy=permit

   quarkus.http.auth.permission.api.paths=/api/*
   quarkus.http.auth.permission.api.policy=authenticated
   ```

   Quarkus applies the longest matching path. `/api/v1/auth/login` is longer than `/api/*`, so login stays open. `/q/*` covers health, OpenAPI, and Swagger UI. Other paths, including `/hello`, stay open.

   `quarkus.oidc.tenant-enabled=false` keeps the OIDC extension, which is already on the classpath, from starting a tenant when HTTP security turns on. Tests already set this as a system property.

3. Do not add `@RolesAllowed` or compare `sub` to a body id in this task. Do not change use cases.

4. Wire the Swagger UI **Authorize** button on every service:

   ```properties
   quarkus.smallrye-openapi.security-scheme=jwt
   ```

   That adds the button. `quarkus.smallrye-openapi.auto-add-security-requirement` only attaches the token to methods annotated with `@RolesAllowed`, and this task does not add those annotations. Without a document-level requirement, **Try it out** will not send `Authorization`.

   Add `src/main/resources/META-INF/openapi.yml` in `dpp-commons` (or confirm Quarkus does not load static OpenAPI documents from a dependency, and then put the same file in each service):

   ```yaml
   openapi: 3.0.3
   info:
     title: Drive Plus Plus
     version: "1.0.0"
   components:
     securitySchemes:
       SecurityScheme:
         type: http
         scheme: bearer
         bearerFormat: JWT
   security:
     - SecurityScheme: []
   ```

   The scheme name `SecurityScheme` is the Quarkus default for `quarkus.smallrye-openapi.security-scheme-name`. Swagger UI adds the `Bearer ` prefix. The value pasted into Authorize is the raw JWT from login, not the word `Bearer`. Sending a token on the public login operation is harmless.

5. Rebuild the jars and the service images. The public key and the auth config are baked in at build time. Traefik routes do not change. Clients that today call a service with no token, including Swagger "Try it out" on `/api/v1`, will get 401 until they log in and pass the token.

6. Prove it on the running containers, not by assuming the path patterns match:

   - `POST http://localhost:8000/api/v1/auth/login` with the admin user returns 200 and a token.
   - `GET http://localhost:8000/api/v1/courses` with no header returns 401.
   - The same GET with `Authorization: Bearer <token>` returns 200.
   - Repeat the unauthenticated GET against one path on each service port: courses 8081, availability or appointments 8082, tests 8083, invoices 8084, a preference URL 8085, notifications 8086, and students or instructors 8080.
   - `GET /q/health/ready` on each port stays 200.
   - Wrong-password login still returns the existing validation error, not the missing-token 401.

   If an unauthenticated `/api/v1/...` call returns 200, the path pattern did not match. Fix the pattern from that response. If startup fails because a second identity provider (OIDC or `quarkus-elytron-security-jdbc`) is active, the log names it. Disable that provider. Do not add a second auth mechanism.

7. Update `user-service/README.md` with the public-key command, the bearer header, and the Swagger steps: execute login, copy the token, open **Authorize**, paste the raw token. Add one line to each other service README: calls need `Authorization: Bearer` from `POST /api/v1/auth/login`, except that login itself, and Swagger **Authorize** takes that same raw token.

## Impacted Services

- `dpp-commons`: `src/main/resources/publicKey.pem` and `src/main/resources/META-INF/openapi.yml` (the bearer scheme Swagger uses). No Kotlin changes. If Quarkus does not load that static document from a dependency, the same `openapi.yml` goes in each service instead.
- `user-service`: auth config, README. Login resource stays public. Signer stays as it is.
- `course-service`, `appointment-service`, `test-service`, `payment-service`, `notification-preference-service`, `notification-service`: the same auth config and a README line.
- Docker images for all seven services, rebuilt from `quarkusBuild`.
- Traefik: no route changes. It forwards the `Authorization` header by default.
- Postgres, RabbitMQ, Flyway: no changes.
- Messaging consumers and `@Scheduled` jobs: no HTTP session, so they are not covered by these permissions and must keep working.

## Architecture Justification

Approach 1 was chosen because the UI will call every service, and the service ports stay published while we develop. A check only in Traefik can be skipped by calling port 8081. A call to user-service on every request adds a hop and makes user-service required for writes that do not otherwise need it. Keycloak replaces the issuer the UI will use. Local verification with the public key matches the design note that checks live in the web layer, and the token already carries the user id and role for a later authorization task.

`dpp-commons` is the one jar every service already loads, so one public key file is enough. The private key stays in user-service and out of git.

## Risk Assessment

- `*.pem` is gitignored. Forgetting the exception for the public key means other checkouts cannot verify tokens.
- Regenerating `privateKey.pem` without regenerating `publicKey.pem` makes every token fail verification.
- Permitting `/q/*` leaves Swagger, OpenAPI, and health public. That is intentional for local testing. Metrics under `/q` are public too.
- A valid token of any role can call any API, including POST. Disabling an account does not invalidate a token until it expires (8 hours). Role and ownership checks are out of scope.
- Existing REST unit tests construct the resource class directly and do not pass through HTTP security. They should keep passing. `ExampleResourceTest` calls `/hello`, which this plan does not protect.
- `@QuarkusTest` repository tests boot the service. A bad auth property can fail startup for all of them. The OIDC tenant must stay disabled.
- Quarkus HTTP permission syntax is easy to get wrong. The curl list above is the proof. A 200 without a token means the pattern missed.
- Service images must be rebuilt. Restarting a container without `quarkusBuild` keeps the old config.

## Execution Checklist

- [x] Generate `dpp-commons/src/main/resources/publicKey.pem` from `user-service/src/main/resources/privateKey.pem`.
- [x] Allow that public key in `.gitignore`. Confirm `privateKey.pem` is still ignored.
- [x] Add the JWT verify and HTTP permission block to all seven `application.properties` files.
- [x] Set `quarkus.oidc.tenant-enabled=false` in those files.
- [x] Set `quarkus.smallrye-openapi.security-scheme=jwt` on all seven services and add the document-level bearer security requirement so Swagger **Try it out** sends `Authorization`.
- [x] In Swagger UI, authorize with the raw login token and confirm a POST sends `Authorization: Bearer` and succeeds.
- [x] Rebuild with `./gradlew quarkusBuild -x test` and recreate the service containers.
- [x] Confirm login without a token returns a token, and a wrong password still returns the existing validation error.
- [x] Confirm an unauthenticated `/api/v1` GET returns 401 on ports 8000, 8080, 8081, 8082, 8083, 8084, 8085, and 8086.
- [x] Confirm the same GET with the bearer token returns 200 through port 8000.
- [x] Confirm `/q/health/ready` returns 200 on each service port.
- [x] Confirm one messaging path still works after the rebuild (enrollment still creates an invoice, or the service logs show the RabbitMQ consumer started).
- [x] Update `user-service/README.md` and add the bearer-token line to the other six service READMEs.
- [x] Run `./gradlew test` for user-service and one other service and confirm existing tests still pass.

## Verification

Login through port 8000 returned 200 and a token. `GET /api/v1/courses` without a token returned 401, and with `Authorization: Bearer` returned 200. The same unauthenticated 401 happened on ports 8080 through 8086. Health returned 200 on each of those ports. `POST /api/v1/courses` without a token returned 401. The same POST with the bearer token returned 201. Course-service Swagger UI shows **Authorize** and a lock on each operation. OpenAPI lists the `SecurityScheme` bearer scheme at document level, loaded from `dpp-commons`. payment-service logged that it is listening on `payment.q.enrollment-events`. `:user-service:test` and `:course-service:test` passed.

A wrong password returns 500 from `QuarkusErrorHandler` with `InvalidCredentialsException`. That is the same handler used before this change. It is not the missing-token 401. `IdentityExceptionMapper` is not applied to that exception.
