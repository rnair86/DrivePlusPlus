# course-service Plan

Complete course-service end to end, following the patterns already established in `user-service`. Source of truth for the domain is section 4.2 of [driving-school-system-design.md](../driving-school-system-design.md).

## Current state

- Done: domain models, ports, use cases, JPA entities, mappers, Panache repositories, RabbitMQ publisher (all match design section 4.2).
- Missing: REST layer, Flyway migration, `application.properties` (empty), CDI/transaction wiring on use cases, tests.
- Broken: leftover Quarkus CLI scaffolding, and tests that reference a non-existent `ExampleResource`, so the module does not build.

## Decisions

- The `user.student.registered` consumer is skipped for now. Enrollment trusts the `studentId` it is given.
- Local development uses the existing database-per-service setup in [local/docker-compose.yml](../local/docker-compose.yml): database `course`, user `course`/`course123`, RabbitMQ user `course_user`. The design's schema-per-service layout is not used locally.

## Steps

### 1. Cleanup and consistency

- [x] Delete standalone build files that conflict with the root multi-project build: `settings.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/`.
- [x] Delete `src/test/.../ExampleResourceTest.kt` and `src/native-test/.../ExampleResourceIT.kt`.
- [x] Rename the package from `org.rnair.driveplusplus` to `org.rnair.driveplusplus.courseservice`, mirroring `org.rnair.driveplusplus.userservice`.
- [x] Move `SessionNotFoundException` from `application/usecase/SessionUseCases.kt` into `core/exceptions/Exceptions.kt`.
- [x] Rename port methods that clash with Panache's `findById`, matching `findByCourseId`/`findByBatchId`:
  - `ClassSessionRepository.findById` to `findBySessionId`
  - `EnrollmentRepository.findById` to `findByEnrollmentId`
- [x] Implement the `courseId`, `instructorId` and `status` filters in `PanacheClassBatchRepository.list` (currently `findAll()`), the same way `PanacheCourseRepository.list` does.

Removed the standalone Gradle wrapper and the generated example tests, then moved sources under `org.rnair.driveplusplus.courseservice`.

### 2. Use case wiring (same pattern as user-service)

- [x] Add `@ApplicationScoped` to every use case class.
- [x] Add `@Transactional` to every mutating `execute`: create/update course, create batch, schedule/reschedule/cancel session, enroll, withdraw. `EnrollStudentUseCase` needs the transaction for the `findByIdForUpdate` row lock to take effect.
- [x] Add `ListSessionsForBatchUseCase` (the repository already has `listByBatch`).

### 3. REST layer (`web/api`, `web/dtos`, `web/providers`)

Every endpoint uses `@RunOnVirtualThread` and maps DTOs in the resource, as `StudentResource` does.

- [x] `CourseResource` at `/api/v1/courses`
  - `POST` creates a course (201)
  - `GET /{id}` returns one course
  - `GET` lists courses (`activeOnly`, `type`, `page`, `size`)
  - `PUT /{id}` updates a course
- [x] `BatchResource` at `/api/v1/batches`
  - `POST` creates a batch (201)
  - `GET` lists batches (`courseId`, `instructorId`, `status`, `page`, `size`)
  - `GET /{id}/sessions` lists a batch's sessions
  - `POST /{id}/sessions` schedules a session (201)
  - `PUT /{id}/sessions/{sessionId}` reschedules a session
  - `DELETE /{id}/sessions/{sessionId}` cancels a session
- [x] `EnrollmentResource` at `/api/v1/enrollments`
  - `POST` with `{studentId, batchId}` enrolls (201)
  - `GET` lists a student's enrollments (`studentId`)
  - `DELETE /{id}` withdraws the enrollment
- [x] DTOs: `CreateCourseRequest`, `CourseResponse`, `CreateBatchRequest`, `BatchResponse`, `ScheduleSessionRequest`, `RescheduleSessionRequest`, `SessionResponse`, `EnrollRequest`, `EnrollmentResponse`.
- [x] `CourseExceptionMapper : BaseExceptionMapper()` annotated with `@Provider`, the same as `IdentityExceptionMapper`.

### 4. Database and config

- [x] Add `src/main/resources/db/migration/V1.0.0__Create_tables.sql` with the course-service DDL from design section 7.3, without the schema prefix:
  - Tables: `courses`, `class_batches`, `class_sessions`, `enrollments`
  - Indexes: `uq_active_enrollment` (partial unique), `idx_enrollments_batch`, `idx_class_batches_instructor`
- [x] Fill in `application.properties`, based on the user-service file:
  - Datasource `jdbc:postgresql://localhost:5432/course` with `course`/`course123`
  - `quarkus.flyway.migrate-at-start=true`
  - `quarkus.http.port=8081` (user-service runs on 8080)
  - `dpp-events-out` RabbitMQ channel with `course_user`/`CoursePassword123`

### 5. Tests (JUnit5 and MockK, same style as user-service)

- [x] Use case tests: `CourseUseCasesTest`, `BatchUseCasesTest`, `SessionUseCasesTest`, `EnrollmentUseCasesTest`. Cover the happy path (including published event type and payload), not-found errors, and `BatchAtCapacityException`/`AlreadyEnrolledException` with nothing saved or published.
- [x] Resource tests: `CourseResourceTest`, `BatchResourceTest`, `EnrollmentResourceTest`, mocking the use cases. Check status codes, request-to-use-case field mapping, and default paging.
- [x] `@QuarkusTest` repository tests against local Postgres, like `PanacheUserAccountRepositoryTest`: batch list filters, and `countActiveForBatch`/`existsActive` after a withdrawal.
- [x] Verify with `./gradlew :course-service:test`.

`./gradlew :course-service:test` passed (41 tests) against the local `course` database.

Issues found while running against Postgres:

- `RabbitMqEventPublisher` was `@ApplicationScoped`. Quarkus could not proxy it because the superclass constructor takes the RabbitMQ emitter. Changed it to `@Singleton`, matching user-service.
- Hibernate wrote camelCase columns (`coursetype`) while Flyway created snake_case columns. Added `@Column` names on the entity fields, matching user-service.

## After course-service (Phase 1 roadmap)

1. RabbitMQ topology: add the `ds.events` and `ds.dlx` exchanges and the queues from design section 6.3 to [local/rabbitmq/definitions.json](../local/rabbitmq/definitions.json), and set the exchange name in each service's outgoing channel config.
2. appointment-service: instructor availability, lesson booking with conflict checks, reminder scheduler.
3. notification-preference-service: per-user preferences, seeded from `user.*.registered` events.
4. notification-service: email only; consumes all events, applies preferences, uses templates, keeps the delivery log.
5. Platform: one Helm chart per service plus an umbrella chart, and gateway routing.

Phase 2 (test-service, payment-service, SMS and push channels, KEDA) follows.
