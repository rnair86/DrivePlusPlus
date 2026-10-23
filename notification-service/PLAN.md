# notification-service Plan

Build notification-service from scratch, following the services already in the repo. Source of truth for the domain is section 4.7 of [driving-school-system-design.md](../driving-school-system-design.md).

## Current state

The module does not exist. Local Postgres already has a `notification` database (`notification` / `notification123`), and RabbitMQ already has `notification_user` / `NotificationPassword123`.

[local/rabbitmq/definitions.json](../local/rabbitmq/definitions.json) already has `dpp.events`, `payment.q.enrollment-events`, and `preferences.q.user-events`. `notification_user` can publish to `dpp.events` and cannot consume any queue yet. Service users cannot declare topology.

`NotificationChannel` and `NotificationEventType` already live in `dpp-commons`. notification-preference-service publishes `preference.updated` with `userId`, `eventType`, `channel`, and `enabled`. It does not publish quiet hours.

Most domain events carry a user id and no email. Only `user.student.registered` and `user.instructor.registered` include `email`. Class session events carry a batch id and no student id. Several published routing keys have no `NotificationEventType`.

## Decisions

- Do not generate a Quarkus CLI project. Gradle module is `notification-service`. Package is `org.rnair.driveplusplus.notificationservice`.
- Local database is `notification` on `localhost:5432`. No `notification_schema`.
- HTTP port is `8086`. Tests set `%test.quarkus.http.test-port=0`.
- `RabbitMqEventPublisher` is `@Singleton` on channel `dpp-events-out`. CamelCase entity fields get explicit `@Column` names.
- Process `EMAIL` only. SMS and PUSH stay on the sender interface and in the template API, and this service does not send them. A missing preference row defaults to enabled, so looping every channel would send SMS and PUSH that nobody opted into.
- The sender is the design's stub. EMAIL returns success and a random provider id. It does not call SES, SendGrid, Twilio, or FCM.
- Do not call user-service. When the payload has `email`, that is the recipient address. Otherwise the address is the user id string. `{{recipient}}` in a template is replaced with that address. No other placeholders are filled in.
- Quiet hours stay out. `preference.updated` does not carry them, and the cache stores `enabled` only.
- Idempotency is `(sourceEventId, userId, channel)`. The design's unique key is `(sourceEventId, channel)`, which would drop the instructor after the student on `appointment.created` and `appointment.reminder.due`. A redelivery of the same user and channel writes nothing and publishes nothing.
- A disabled preference writes `SUPPRESSED_BY_PREFERENCE` and publishes nothing. A missing template writes nothing and publishes nothing, so a later template still allows a redelivery to send. A successful send writes `SENT` and publishes `notification.sent`. A failed send writes `FAILED` and publishes `notification.failed`.
- `isEnabled` is true when the cache has no row, as designed.
- Events with no `NotificationEventType`, and events with no user id, are ignored. That covers `user.status.changed`, `class.batch.created`, `class.session.scheduled`, `class.session.rescheduled`, `class.session.cancelled`, `course.enrollment.created`, `course.enrollment.withdrawn`, `appointment.rescheduled`, `test.rescheduled`, `test.cancelled`, and `payment.invoice.created`.
- Mapped events:

  | Routing key | Notification type | Recipients |
  |---|---|---|
  | `user.student.registered` | `STUDENT_REGISTERED` | `studentId`, address `email` |
  | `user.instructor.registered` | `INSTRUCTOR_REGISTERED` | `instructorId`, address `email` |
  | `appointment.created` | `APPOINTMENT_CREATED` | `studentId` and `instructorId` |
  | `appointment.reminder.due` | `APPOINTMENT_REMINDER` | `studentId` and `instructorId` |
  | `appointment.cancelled` | `APPOINTMENT_CANCELLED` | `studentId` |
  | `test.scheduled` | `TEST_SCHEDULED` | `studentId` |
  | `test.reminder.due` | `TEST_REMINDER` | `studentId` |
  | `test.result.published` | `TEST_RESULT_PUBLISHED` | `studentId` |
  | `payment.reminder.due` | `PAYMENT_DUE` | `studentId` |
  | `payment.overdue` | `PAYMENT_OVERDUE` | `studentId` |
  | `payment.received` | `PAYMENT_RECEIVED` | `studentId` |

- Own queues, not shared with notification-preference-service. Bindings are on `dpp.events`:
  - `notif.q.user-events` ← `user.#`
  - `notif.q.class-events` ← `class.#` and `course.enrollment.#`
  - `notif.q.appointment-events` ← `appointment.#`
  - `notif.q.test-events` ← `test.#`
  - `notif.q.payment-events` ← `payment.#`
  - `notif.q.preference-updates` ← `preference.updated`
- One listener method per queue. The payload is a concrete envelope with a Jackson tree, not `EventEnvelope<T>`. Mapping from routing key to recipients stays in infrastructure.
- `preference.updated` updates the cache and does not send a notification.
- Template save updates the row for the same `eventType` and `channel` when one exists. The unique key is that pair.
- History filters in the query by the provided `userId`, `eventType`, `from`, and `to`. No paging.
- Seed `V2` inserts one EMAIL template for each mapped notification type. Body text includes `{{recipient}}`.
- The dead-letter exchange stays out. No scheduler. `notification_user` read permission becomes the six `notif.q.*` queues.

## Steps

### 1. Module

- [x] Add `include("notification-service")` to [settings.gradle.kts](../settings.gradle.kts).
- [x] Add `build.gradle.kts` with the `dpp.quarkus-service` plugin, `dpp-commons`, and MockK. No scheduler dependency.

### 2. Domain

Package `core.models`, `core.repository`. No framework imports. Reuse `NotificationChannel` and `NotificationEventType` from `dpp-commons`.

- [x] `DeliveryStatus`: `SENT`, `FAILED`, `SUPPRESSED_BY_PREFERENCE`.
- [x] `Recipient` (`userId`, `address`).
- [x] `NotificationLogEntry`, `PreferenceCacheEntry`, `NotificationTemplate`, `SendResult`.
- [x] `NotificationTemplate.render` replaces `{{recipient}}` with `recipient.address`.
- [x] `NotificationLogRepository`: `existsFor(sourceEventId, userId, channel)`, `save`, `history`.
- [x] `PreferenceCacheRepository`: `isEnabled`, `upsert`.
- [x] `TemplateRepository`: `find`, `save`, `list`.
- [x] `NotificationSender.send`.

### 3. Use cases

`@ApplicationScoped` on every use case. `@Transactional` on every mutating `execute`.

- [x] `ProcessDomainEventUseCase`. For each recipient, EMAIL only, in the order above: existing row stops the channel, a disabled preference logs `SUPPRESSED_BY_PREFERENCE`, a missing template returns, otherwise send, log, and publish `notification.sent` or `notification.failed` with `NotificationOutcomePayload`.
- [x] `UpdatePreferenceCacheUseCase`.
- [x] `GetNotificationHistoryUseCase`.
- [x] `UpdateTemplateUseCase`.
- [x] `ListTemplatesUseCase`.

### 4. Persistence and messaging

- [x] `NotificationLogJpaEntity` (`notification_log`) extends `BaseJpaEntity`, with `@Column` for `user_id`, `source_event_id`, `event_type`, `provider_message_id`, `sent_at`, `error_message`.
- [x] `PreferenceCacheJpaEntity` (`preference_cache`) with composite id `(userId, eventType, channel)`. Query those columns. Do not call Panache `findById` for the composite key.
- [x] `TemplateJpaEntity` (`templates`) extends `BaseJpaEntity`, with `@Column` for `event_type`, `subject_template`, `body_template`.
- [x] Mappers for the log and the template.
- [x] `PanacheNotificationLogRepository`. `save` persists. `history` applies only the filters that were passed.
- [x] `PanachePreferenceCacheRepository`. `isEnabled` returns true when the row is missing. `upsert` updates `enabled` when the key exists.
- [x] `PanacheTemplateRepository`. `save` updates subject and body when the event type and channel already exist.
- [x] `LoggingEmailSender` (stub success), `SmsSender`, and `PushSender`, behind `CompositeNotificationSender`.
- [x] `RabbitMqEventPublisher` as `@Singleton` on channel `dpp-events-out`.
- [x] `DomainEventListener` with `@Incoming` for `user-events`, `class-events`, `appointment-events`, `test-events`, and `payment-events`, each `@RunOnVirtualThread`. It maps the routing key and calls `ProcessDomainEventUseCase` only for the table above.
- [x] `PreferenceUpdatedListener` on `preference-updates`. Concrete payload `userId`, `eventType`, `channel`, `enabled`. Unknown enum names are ignored.

### 5. REST layer

Every endpoint uses `@RunOnVirtualThread` and maps DTOs in the resource.

- [x] `GET /api/v1/notifications` with optional `userId`, `eventType`, `from`, `to`. Returns 200.
- [x] `GET /api/v1/templates` returns 200.
- [x] `PUT /api/v1/templates` saves one template. Body: `eventType`, `channel`, `subjectTemplate`, `bodyTemplate`. Returns 200.
- [x] DTOs: `NotificationLogResponse`, `TemplateRequest`, `TemplateResponse`.
- [x] `NotificationExceptionMapper : BaseExceptionMapper()` with `@Provider`.

### 6. Database, config, and broker

- [x] `V1.0.0__Create_tables.sql` from design section 7.3, without a schema prefix:
  - `notification_log`, unique `(source_event_id, user_id, channel)`
  - `preference_cache`, primary key `(user_id, event_type, channel)`
  - `templates`, unique `(event_type, channel)`
  - `channel` checks `EMAIL`, `SMS`, `PUSH`
  - `status` check `SENT`, `FAILED`, `SUPPRESSED_BY_PREFERENCE`
- [x] `V2.0.0__Seed_email_templates.sql` inserts one EMAIL row for each mapped notification type.
- [x] `application.properties`:
  - `jdbc:postgresql://localhost:5432/notification` with `notification` / `notification123`
  - `quarkus.flyway.migrate-at-start=true`
  - `quarkus.http.port=8086`
  - `%test.quarkus.http.test-port=0`
  - outgoing `dpp-events-out` with `notification_user` / `NotificationPassword123`, exchange `dpp.events`, `exchange.declare=false`
  - one incoming channel per queue above, `exchange.declare=false`, `queue.declare=false`
- [x] Add the six queues and their bindings to [local/rabbitmq/definitions.json](../local/rabbitmq/definitions.json).
- [x] Set `notification_user` read permission to those six queues. Write stays `^dpp\.events$`. Configure stays `^$`.
- [x] Apply the same queues, bindings, and permission to the running broker.

### 7. Tests

JUnit 5 and MockK for use cases, mapping, listeners, and resources. `@QuarkusTest` and `@TestTransaction` for repositories, against the local `notification` database.

- [x] Use case tests: EMAIL send publishes `notification.sent` and stores the provider id; a second pass for the same user and channel publishes nothing; a disabled preference stores `SUPPRESSED_BY_PREFERENCE` and publishes nothing; a missing template stores nothing; a failed send publishes `notification.failed`; two recipients on one event both send; SMS is not attempted; cache upsert then `isEnabled` follows the stored flag; history passes the filters through; template save returns the stored subject and body.
- [x] Mapping tests: each routed key in the table produces the right type and recipients; an unmapped key produces nothing; a registration uses the email as the address.
- [x] Listener tests: a domain envelope is forwarded to the use case; `preference.updated` is forwarded to the cache use case.
- [x] Resource tests: status codes and query or body fields forwarded to the use case.
- [x] Repository tests: `existsFor` is per user; history filters out other users and dates; template save updates the same event type and channel; `isEnabled` is true when the cache row is missing.
- [x] `./gradlew :notification-service:test`.

`./gradlew :notification-service:test` passed: 21 tests, 0 failures, against the local `notification` database.

The running RabbitMQ volume does not reload `definitions.json`. The six `notif.q.*` queues, their bindings, and the `notification_user` read permission were applied through the management API on the running broker.
