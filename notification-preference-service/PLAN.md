# notification-preference-service Plan

Build notification-preference-service from scratch, following the services already in the repo. Source of truth for the domain is section 4.6 of [driving-school-system-design.md](../driving-school-system-design.md).

notification-service stays the service after this one. It consumes `preference.updated` and needs this service's events before it can keep a preference cache.

## Current state

The module does not exist. Local Postgres already has a `preferences` database (`preferences` / `preferences123`), and RabbitMQ already has `preferences_user` / `PreferencesPassword123`.

[local/rabbitmq/definitions.json](../local/rabbitmq/definitions.json) already has the topic exchange `dpp.events` and the payment enrollment queue. Service users still have configure permission only on `^dpp-events-out$`, so this service must not declare its exchange or queue. The shared exchange constant is `dpp.events` in [RabbitTopology.kt](../dpp-commons/src/main/kotlin/messaging/RabbitTopology.kt).

user-service already publishes `user.student.registered` (`StudentRegisteredPayload`: `studentId`, `email`, `firstName`, `lastName`) and `user.instructor.registered` (`InstructorRegisteredPayload`: `instructorId`, `email`, `firstName`, `lastName`).

`NotificationChannel` and `NotificationEventType` already live in `dpp-commons`. This service uses those enums. It does not add new ones.

## Decisions

- Do not generate a Quarkus CLI project. Gradle module is `notification-preference-service`. Package is `org.rnair.driveplusplus.notificationpreferenceservice`.
- Local database is `preferences` on `localhost:5432`. No `notification_pref_schema`.
- HTTP port is `8085`. Tests set `%test.quarkus.http.test-port=0`.
- `RabbitMqEventPublisher` is `@Singleton` on channel `dpp-events-out`. CamelCase entity fields get explicit `@Column` names.
- The row key is `(userId, eventType, channel)`. Repository methods are `upsert`, `findAllForUser`, and `findOne`. Do not call Panache `findById` for that composite key; query the three columns.
- Enums are stored as their names (`EMAIL`, `APPOINTMENT_CREATED`).
- `upsert` inserts when the key is missing and otherwise updates `enabled`, `quietHoursStart`, and `quietHoursEnd`.
- Upsert and toggle each publish one `preference.updated` event per saved row, payload `PreferenceUpdatedPayload` (`userId`, `eventType`, `channel`, `enabled`). Quiet hours are not on the event.
- A bulk upsert writes and publishes only the rows in the request. Rows left out of the request stay as they are.
- Toggle changes `enabled` and keeps the quiet hours already stored. A toggle of a missing row inserts it with null quiet hours.
- Seed default preferences when a registration event arrives. Seeding publishes nothing. Seeding is an upsert, so a redelivered registration does not create a second row.
- Student seed, channel `EMAIL`, enabled, quiet hours null:
  - `APPOINTMENT_CREATED`, `APPOINTMENT_REMINDER`, `TEST_SCHEDULED`, `TEST_REMINDER`, `TEST_RESULT_PUBLISHED`, `PAYMENT_DUE`, `PAYMENT_OVERDUE`
- Any other role, including instructor, seeds `APPOINTMENT_CREATED` and `CLASS_SESSION_SCHEDULED` on `EMAIL`.
- Wire the consumer now. Do not share `notif.q.user-events` with notification-service. One RabbitMQ queue delivers each message to one consumer, and both services need every registration. This service gets its own durable queue `preferences.q.user-events`, bound to `user.student.registered` and `user.instructor.registered`.
- One listener on that queue. It branches on `eventType`. Jackson reads a concrete payload (`studentId` or `instructorId`, plus `email`, `firstName`, `lastName`). It does not use `EventEnvelope<T>`.
- The dead-letter exchange stays out of this plan. No scheduler.

## Steps

### 1. Module

- [x] Add `include("notification-preference-service")` to [settings.gradle.kts](../settings.gradle.kts).
- [x] Add `build.gradle.kts` with the `dpp.quarkus-service` plugin, `dpp-commons`, and MockK. No scheduler dependency.

### 2. Domain

Package `core.models`, `core.repository`. No framework imports. Reuse `NotificationChannel` and `NotificationEventType` from `dpp-commons`.

- [x] `NotificationPreference` (`userId`, `eventType`, `channel`, `enabled`, `quietHoursStart`, `quietHoursEnd`).
- [x] `NotificationPreferenceRepository`: `upsert`, `findAllForUser`, `findOne`.

### 3. Use cases

`@ApplicationScoped` on every use case. `@Transactional` on every mutating `execute`.

- [x] `UpsertPreferencesUseCase`. Upserts each preference under the path `userId` and publishes `preference.updated` for each.
- [x] `GetPreferenceMatrixUseCase`.
- [x] `TogglePreferenceUseCase`. Upserts `enabled` and keeps existing quiet hours. Publishes `preference.updated`.
- [x] `SeedDefaultPreferencesUseCase`. Student list or instructor list above, `EMAIL` only. Publishes nothing.

### 4. Persistence and messaging

- [x] `NotificationPreferenceJpaEntity` (`notification_preferences`) with `@Column` for `user_id`, `event_type`, `quiet_hours_start`, `quiet_hours_end`. Composite id `(userId, eventType, channel)`.
- [x] `NotificationPreferenceMapper`.
- [x] `PanacheNotificationPreferenceRepository`. `upsert` updates the three mutable fields when the key exists.
- [x] `RabbitMqEventPublisher` as `@Singleton` on channel `dpp-events-out`.
- [x] `UserRegisteredListener` in `infrastructure.messaging`. `@Incoming("user-events")` and `@RunOnVirtualThread`. `user.student.registered` calls seed with the student id and `STUDENT`. `user.instructor.registered` calls seed with the instructor id and `INSTRUCTOR`.

### 5. REST layer

Every endpoint uses `@RunOnVirtualThread` and maps DTOs in the resource. Base path `/api/v1/users/{userId}/preferences`.

- [x] `GET` returns that user's preferences (200).
- [x] `PUT` upserts a list (200). Each item: `eventType`, `channel`, `enabled`, `quietHoursStart`, `quietHoursEnd`.
- [x] `PUT /{eventType}/{channel}` toggles `enabled` (200). Body: `enabled`.
- [x] DTOs: `UpsertPreferenceRequest`, `UpsertPreferencesRequest`, `TogglePreferenceRequest`, `PreferenceResponse`.
- [x] `PreferenceExceptionMapper : BaseExceptionMapper()` with `@Provider`.

### 6. Database, config, and broker

- [x] `src/main/resources/db/migration/V1.0.0__Create_tables.sql` from design section 7.3, without a schema prefix:
  - `notification_preferences`
  - primary key `(user_id, event_type, channel)`
  - `channel` check `EMAIL`, `SMS`, `PUSH`
- [x] `application.properties`:
  - `jdbc:postgresql://localhost:5432/preferences` with `preferences` / `preferences123`
  - `quarkus.flyway.migrate-at-start=true`
  - `quarkus.http.port=8085`
  - `%test.quarkus.http.test-port=0`
  - outgoing `dpp-events-out` with `preferences_user` / `PreferencesPassword123`, exchange `dpp.events`, `exchange.declare=false`
  - incoming `user-events` on the same broker, queue `preferences.q.user-events`, `exchange.declare=false`, `queue.declare=false`
- [x] Add to [local/rabbitmq/definitions.json](../local/rabbitmq/definitions.json): durable queue `preferences.q.user-events`, bindings for `user.student.registered` and `user.instructor.registered` on `dpp.events`.
- [x] The running RabbitMQ volume will not reload `definitions.json`. Apply the same queue and bindings to the running broker.

### 7. Tests

JUnit 5 and MockK for use cases, the listener, and the resource. `@QuarkusTest` and `@TestTransaction` for the repository, against the local `preferences` database.

- [x] Use case tests: bulk upsert publishes one `preference.updated` per row and does not delete other rows; a second upsert of the same key updates enabled and quiet hours; toggle of an existing row changes enabled and keeps quiet hours; toggle of a missing row inserts null quiet hours; student seed writes the seven EMAIL rows and publishes nothing; instructor seed writes the two EMAIL rows; seeding the same user again leaves one row per key.
- [x] Listener test: a student payload and an instructor payload are forwarded to `SeedDefaultPreferencesUseCase` with the right id and role.
- [x] Resource tests: status codes, path `userId` wins over any body user id, request fields forwarded to the use case.
- [x] Repository test: `upsert` inserts, then updates the same key, and `findAllForUser` does not return another user's rows.
- [x] `./gradlew :notification-preference-service:test`.

`./gradlew :notification-preference-service:test` passed: 11 tests, 0 failures, against the local `preferences` database.

The running RabbitMQ volume does not reload `definitions.json`. Queue `preferences.q.user-events` and the `user.student.registered` and `user.instructor.registered` bindings were applied through the management API on the running broker.
