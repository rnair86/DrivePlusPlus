# test-service Plan

Build test-service from scratch, following `user-service`, `course-service`, and `appointment-service`. Source of truth for the domain is section 4.4 of [driving-school-system-design.md](../driving-school-system-design.md).

## Current state

The module does not exist. Local Postgres already has a `test` database (`test` / `test123`), and RabbitMQ already has `test_user` / `TestPassword123`.

## Decisions

- Do not generate a Quarkus CLI project. Add the module by hand.
- Gradle module name is `test-service`. A module named `test` would clash with Gradle's `test` task. Package is `org.rnair.driveplusplus.testservice`.
- Local database is `test` on `localhost:5432`. No `test_schema`.
- HTTP port is `8083`. Tests set `%test.quarkus.http.test-port=0`, because Quarkus tests otherwise bind 8081.
- The catalog's `course.enrollment.created` consumer is marked reserved and section 4.4 defines no consumer. Skip it. Scheduling trusts the `studentId` it is given.
- `RabbitMqEventPublisher` is `@Singleton`. Entity camelCase fields get explicit `@Column` names. `findById` is named `findByTestScheduleId`. `save` merges when the id already exists, so reschedule updates the row.
- Reminders stay as designed: status `SCHEDULED` and `reminderSentAt` null, starting within 24 hours. A `RESCHEDULED` test is not reminded.
- Status rules, from the design plus the chosen guard:
  - Reschedule rejects `COMPLETED` only (`TestAlreadyCompletedException`). A `CANCELLED` test can still be rescheduled, which sets `RESCHEDULED`.
  - Cancel and record-result reject both `COMPLETED` and `CANCELLED`. A result can be recorded only while `SCHEDULED` or `RESCHEDULED`.
  - Recording a result sets status to `COMPLETED`, stores the result and notes, and publishes `test.result.published`.
  - `TestAlreadyCancelledException` is a new `ConflictException`. The design only defined the completed one.

## Steps

### 1. Module

- [x] Add `include("test-service")` to [settings.gradle.kts](../settings.gradle.kts).
- [x] Add `build.gradle.kts` with the `dpp.quarkus-service` plugin, `dpp-commons`, MockK, and `implementation("io.quarkus:quarkus-scheduler")`.

### 2. Domain

Package `core.models`, `core.repository`, `core.exceptions`. No framework imports.

- [x] `TestScheduleId`.
- [x] `TestType`: `MOCK_THEORY`, `MOCK_PRACTICAL`, `OFFICIAL_THEORY`, `OFFICIAL_ROAD`.
- [x] `TestScheduleStatus`: `SCHEDULED`, `RESCHEDULED`, `CANCELLED`, `COMPLETED`.
- [x] `TestResult`: `PASS`, `FAIL`, `PENDING`.
- [x] `TestSchedule` (`studentId`, `testType`, `scheduledAt`, `location`, `examinerOrInstructorId`, `status`, `result`, `resultNotes`, `reminderSentAt`).
- [x] `TestScheduleRepository`: `save`, `findByTestScheduleId`, `listByStudent`, `findStartingWithinWithNoReminderSent`, `markReminderSent`, `updateStatus`, `recordResult`.
- [x] `TestScheduleNotFoundException`, `TestAlreadyCompletedException`, `TestAlreadyCancelledException`.

### 3. Use cases

`@ApplicationScoped` on every use case. `@Transactional` on every mutating `execute`.

- [x] `ScheduleTestUseCase`. Saves status `SCHEDULED` and publishes `test.scheduled` with `TestScheduledPayload` (`testId`, `studentId`, `testType` name, `scheduledAt`).
- [x] `RescheduleTestUseCase`. Rejects `COMPLETED`, saves the new time with status `RESCHEDULED`, publishes `test.rescheduled` (`testId`, `oldTime`, `newTime`).
- [x] `CancelTestUseCase`. Rejects `COMPLETED` and `CANCELLED`, sets `CANCELLED`, publishes `test.cancelled` (`testId`, `studentId`).
- [x] `RecordTestResultUseCase`. Rejects `COMPLETED` and `CANCELLED`, records the result, publishes `test.result.published` (`testId`, `studentId`, `result` name).
- [x] `ListTestsForStudentUseCase`.
- [x] `FindDueReminderTestsUseCase`. For each test starting within 24 hours, publishes `test.reminder.due` with `TestReminderDuePayload`, then marks the reminder sent.

### 4. Persistence and messaging

- [x] `TestScheduleJpaEntity` on `test_schedules`, with `@Column` for `student_id`, `test_type`, `scheduled_at`, `examiner_or_instructor_id`, `result_notes`, `reminder_sent_at`.
- [x] `TestScheduleMapper`.
- [x] `PanacheTestScheduleRepository`.
  - `recordResult` sets `result`, `resultNotes`, and status `COMPLETED`.
  - Due reminders: `scheduledAt <= now + withinHours`, `reminderSentAt is null`, status `SCHEDULED`.
- [x] `RabbitMqEventPublisher` as `@Singleton` on channel `dpp-events-out`.
- [x] `TestReminderJob` in `infrastructure.scheduling`: `@Scheduled(every = "15m")` and `@RunOnVirtualThread`, calling `FindDueReminderTestsUseCase`.

### 5. REST layer

Every endpoint uses `@RunOnVirtualThread` and maps DTOs in the resource. Base path `/api/v1/tests`.

- [x] `POST` schedules a test (201). Body: `studentId`, `testType`, `scheduledAt`, `location`, `examinerOrInstructorId`.
- [x] `GET` lists a student's tests (`studentId`).
- [x] `PUT /{id}` reschedules. Body: `scheduledAt`.
- [x] `DELETE /{id}` cancels (204).
- [x] `POST /{id}/result` records a result (204). Body: `result`, `notes`.
- [x] DTOs: `ScheduleTestRequest`, `RescheduleTestRequest`, `RecordTestResultRequest`, `TestScheduleResponse`.
- [x] `TestExceptionMapper : BaseExceptionMapper()` with `@Provider`.

### 6. Database and config

- [x] `src/main/resources/db/migration/V1.0.0__Create_tables.sql` from design section 7.3, without a schema prefix:
  - `test_schedules`
  - indexes `idx_test_schedules_student`, `idx_test_schedules_reminder`
- [x] `application.properties`:
  - `jdbc:postgresql://localhost:5432/test` with `test` / `test123`
  - `quarkus.flyway.migrate-at-start=true`
  - `quarkus.http.port=8083`
  - `%test.quarkus.http.test-port=0`
  - `dpp-events-out` with `test_user` / `TestPassword123`

### 7. Tests

JUnit 5 and MockK for use cases and resources. `@QuarkusTest` and `@TestTransaction` for the repository, against the local `test` database.

- [x] Use case tests: schedule publishes `test.scheduled`; reschedule of a completed test publishes nothing; reschedule publishes `test.rescheduled`; cancel and record-result reject `COMPLETED` and `CANCELLED` with nothing written or published; a successful result publishes `test.result.published`; reminder publishes `test.reminder.due` and marks sent.
- [x] Resource tests: status codes, request fields forwarded to the use case, conflict exceptions left to the mapper.
- [x] Repository tests: `recordResult` stores the result and sets `COMPLETED`; due-reminder query ignores rows that already have `reminderSentAt`, are not `SCHEDULED`, or start outside 24 hours; `save` updates an existing row.
- [x] `./gradlew :test-service:test`.

`./gradlew :test-service:test` passed against the local `test` database.

`recordResult` is a bulk update, so Hibernate keeps the old row in the persistence context. The repository test clears that context before reading the stored result back.
