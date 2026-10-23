# Plan: Rename JWT issuer to user-service

## Brief Description

Change the JWT `iss` claim and every `mp.jwt.verify.issuer` value from `identity-service` to `user-service`. The running service that signs tokens is user-service. The old string is a leftover name from the design document and does not name a process.

## Implementation Strategy

`SmallRyeTokenIssuer` writes the claim with `Jwt.issuer(...)`. Each service rejects a token whose `iss` does not equal `mp.jwt.verify.issuer`. Both sides must use the same string.

1. Set `.issuer("user-service")` in `user-service/.../SmallRyeTokenIssuer.kt`.
2. Set `mp.jwt.verify.issuer=user-service` in all seven `application.properties` files.
3. Leave event `source = "identity-service"` unchanged. That field is on published messages, not on the token.
4. Rebuild the service images and recreate the containers. The packaged config is what the containers verify against. Tokens issued before the change fail until the caller logs in again.

## Impacted Services

- user-service: signer and its own verifier.
- course-service, appointment-service, test-service, payment-service, notification-preference-service, notification-service: verifier config only.
- Local images from `local/docker-compose.services.yml`.

## Architecture Justification

The issuer is a claim both sides already share. Renaming it does not add a lookup, a new key, or a new service. Keeping the signer and the verifiers on one string is the existing check.

## Risk Assessment

- A token signed with `iss=identity-service` is rejected after the verifiers change. Callers log in again.
- Rebuilding only user-service leaves the other containers expecting `identity-service`, so a new token fails there. All seven images have to be rebuilt.
- Event consumers that read `source` are unaffected because that value is not changed.

## Execution Checklist

- [x] Set the issuer to `user-service` in `SmallRyeTokenIssuer`.
- [x] Set `mp.jwt.verify.issuer=user-service` in all seven `application.properties` files.
- [x] Confirm event `source` values are still `identity-service`.
- [x] Rebuild the seven service images and recreate the containers.
- [x] Log in and confirm the token `iss` claim is `user-service`.
- [x] Confirm a call with that token succeeds and a call without a token still returns 401.
