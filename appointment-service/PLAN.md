# appointment-service Plan

Build appointment-service from scratch, following the patterns in `user-service` and `course-service`. Source of truth for the domain is section 4.3 of [driving-school-system-design.md](../driving-school-system-design.md).

## Current state

The module does not exist. Local Postgres already has an `appointment` database (`appointment` / `appointment123`), and RabbitMQ already has `appointment_user` / `AppointmentPassword123`.

## Decisions

- Do not generate a Quarkus CLI project. Add the module by hand so there is no stray Gradle wrapper or example resource.
- Package is `org.rnair.driveplusplus.appointmentservice`.
- Local database is `appointment` on `localhost:5432`. No `appointment_schema`.
- HTTP port is `8082` (`user-service` is 8080, `course-service` is 8081).
- The catalog's `course.enrollment.created` consumer is marked reserved and section 4.3 defines no consumer. Skip it. Booking trusts the `studentId` and `instructorId` it is given.
- Booking does not check that the time falls inside a defined availability slot. The design's `BookAppointmentUseCase` only rejects overlapping appointments.
- Reminder query stays as designed: status `CONFIRMED` and `reminderSentAt` null, starting within 24 hours. A `RESCHEDULED` appointment is not reminded.
- `RabbitMqEventPublisher` is `@Singleton`. `@ApplicationScoped` cannot be proxied because the shared publisher constructor takes the emitter.
- Entity fields that are camelCase get explicit `@Column` names. Hibernate otherwise writes `startat` instead of `start_at`.
- `AppointmentRepository.findById` is named `findByAppointmentId` so it does not clash with Panache's `findById`.
- Reschedule must ignore the appointment being moved. The design's overlap query would treat the appointment as conflicting with itself.

## Steps

### 1. Module

- [ ] Add `include("appointment-service")` to [settings.gradle.kts](../settings.gradle.kts).
- [ ] Add `build.gradle.kts` with the `dpp.quarkus-service` plugin, `dpp-commons`, and MockK, same as [course-service/build.gradle.kts](../course-service/build.gradle.kts).
- [ ] Add `implementation("io.quarkus:quarkus-scheduler")`. The shared plugin does not include it, and the reminder job needs `@Scheduled`.

### 2. Domain

Package `core.models`, `core.repository`, `core.exceptions`. No framework imports.

- [ ] `AppointmentId`, `AvailabilitySlotId`.
- [ ] `AppointmentStatus`: `CONFIRMED`, `RESCHEDULED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`.
- [ ] `AvailabilitySlot` (`instructorId`, `dayOfWeek`, `startTime`, `endTime`) and `Appointment` (`studentId`, `instructorId`, `vehicleId`, `startAt`, `endAt`, `pickupLocation`, `status`, `notes`, `reminderSentAt`).
- [ ] `AvailabilityRepository`: `save`, `findForInstructor`.
- [ ] `AppointmentRepository`: `save`, `findByAppointmentId`, `overlapsExisting`, `list`, `findStartingWithinWithNoReminderSent`, `markReminderSent`, `updateStatus`.
- [ ] `overlapsExisting` takes an optional `excludeId` so a reschedule does not collide with the row it is updating.
- [ ] `AppointmentNotFoundException`, `SlotConflictException`.

### 3. Use cases

`@ApplicationScoped` on every use case. `@Transactional` on every mutating `execute`.

- [ ] `DefineAvailabilityUseCase`, `GetAvailableSlotsUseCase`.
- [ ] `BookAppointmentUseCase`. Rejects an overlap, saves with status `CONFIRMED` and a null vehicle, publishes `appointment.created` with `AppointmentCreatedPayload`.
- [ ] `RescheduleAppointmentUseCase`. Loads the appointment, checks overlap excluding itself, saves `RESCHEDULED`, publishes `appointment.rescheduled` (`appointmentId`, `oldStart`, `newStart`).
- [ ] `CancelAppointmentUseCase`. Publishes `appointment.cancelled` (`appointmentId`, `studentId`).
- [ ] `CompleteAppointmentUseCase`. Sets `COMPLETED`. No event.
- [ ] `ListAppointmentsUseCase`.
- [ ] `FindDueReminderAppointmentsUseCase`. For each appointment starting within 24 hours, publishes `appointment.reminder.due` with `AppointmentReminderDuePayload`, then marks the reminder sent.

### 4. Persistence and messaging

- [ ] `AvailabilitySlotJpaEntity` (`instructor_availability`) and `AppointmentJpaEntity` (`appointments`), with `@Column` for `instructor_id`, `day_of_week`, `start_time`, `end_time`, `student_id`, `vehicle_id`, `start_at`, `end_at`, `pickup_location`, `reminder_sent_at`.
- [ ] `AvailabilitySlotMapper`, `AppointmentMapper`.
- [ ] `PanacheAvailabilityRepository`.
- [ ] `PanacheAppointmentRepository`.
  - Overlap: same instructor, status not `CANCELLED`, `startAt < endAt` and `endAt > startAt`, optionally `id != excludeId`.
  - `list` filters on the non-null `studentId`, `instructorId`, `from`, and `to` values. The design leaves this as `findAll()`.
  - Due reminders: `startAt <= now + withinHours`, `reminderSentAt is null`, status `CONFIRMED`.
- [ ] `RabbitMqEventPublisher` as `@Singleton` on channel `dpp-events-out`.
- [ ] `AppointmentReminderJob` in `infrastructure.scheduling`: `@Scheduled(every = "15m")` and `@RunOnVirtualThread`, calling `FindDueReminderAppointmentsUseCase`.

### 5. REST layer

Every endpoint uses `@RunOnVirtualThread` and maps DTOs in the resource.

- [ ] `AvailabilityResource` at `/api/v1/availability`
  - `POST` defines a slot (201). Body: `instructorId`, `dayOfWeek`, `startTime`, `endTime`.
  - `GET` lists slots for `instructorId`.
- [ ] `AppointmentResource` at `/api/v1/appointments`
  - `POST` books (201). Body: `studentId`, `instructorId`, `startAt`, `endAt`, `pickupLocation`.
  - `GET` lists with `studentId`, `instructorId`, `from`, `to`.
  - `PUT /{id}` reschedules. Body: `startAt`, `endAt`.
  - `DELETE /{id}` cancels (204).
  - `POST /{id}/complete` completes (204).
- [ ] DTOs: `DefineAvailabilityRequest`, `AvailabilityResponse`, `BookAppointmentRequest`, `RescheduleAppointmentRequest`, `AppointmentResponse`.
- [ ] `AppointmentExceptionMapper : BaseExceptionMapper()` with `@Provider`.

### 6. Database and config

- [ ] `src/main/resources/db/migration/V1.0.0__Create_tables.sql` from design section 7.3, without a schema prefix:
  - `instructor_availability`, `appointments`
  - indexes `idx_appointments_instructor_time`, `idx_appointments_student`, `idx_appointments_reminder`
- [ ] `application.properties`:
  - `jdbc:postgresql://localhost:5432/appointment` with `appointment` / `appointment123`
  - `quarkus.flyway.migrate-at-start=true`
  - `quarkus.http.port=8082`
  - `dpp-events-out` with `appointment_user` / `AppointmentPassword123`

### 7. Tests

JUnit 5 and MockK for use cases and resources. `@QuarkusTest` and `@TestTransaction` for repositories, against the local `appointment` database.

- [ ] Use case tests: book publishes `appointment.created`; overlap publishes nothing; reschedule excludes itself and publishes `appointment.rescheduled`; missing appointment; cancel and complete; reminder publishes `appointment.reminder.due` and marks sent.
- [ ] Resource tests: status codes, request fields forwarded to the use case, conflict exception left to the mapper.
- [ ] Repository tests: overlap ignores `CANCELLED` and the excluded id; list filters; due-reminder query ignores rows that already have `reminderSentAt` or are not `CONFIRMED`.
- [ ] `./gradlew :appointment-service:test`.
