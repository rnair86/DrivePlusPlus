# payment-service Plan

Build payment-service from scratch, following the services already in the repo. Source of truth for the domain is section 4.5 of [driving-school-system-design.md](../driving-school-system-design.md).

## Current state

The module does not exist. Local Postgres already has a `payment` database (`payment` / `payment123`), and RabbitMQ already has `payment_user` / `PaymentPassword123`.

[local/rabbitmq/definitions.json](../local/rabbitmq/definitions.json) declares users only. No exchange or queue exists. Publishers do not set an exchange name. The shared constant is `dpp.events` in [RabbitTopology.kt](../dpp-commons/src/main/kotlin/messaging/RabbitTopology.kt). The design document calls the same exchange `ds.events`; this plan uses `dpp.events`.

## Decisions

- Do not generate a Quarkus CLI project. Gradle module is `payment-service`. Package is `org.rnair.driveplusplus.paymentservice`.
- Local database is `payment` on `localhost:5432`. No `payment_schema`.
- HTTP port is `8084`. Tests set `%test.quarkus.http.test-port=0`.
- `RabbitMqEventPublisher` is `@Singleton`. CamelCase entity fields get explicit `@Column` names. `InvoiceRepository.findById` is named `findByInvoiceId`. `save` merges when the id already exists.
- Partial payments are allowed. An `OVERDUE` invoice can still be paid. Status becomes `PAID` only when the paid total equals the invoice amount. `PAID` and `CANCELLED` reject further payments.
- Compare money with `BigDecimal.compareTo`. The design's `==` treats `10.0` and `10.00` as different.
- Cancel rejects `PAID` (`InvoiceAlreadyPaidException`) and already `CANCELLED` (`InvoiceCancelledException`). `PENDING` and `OVERDUE` can be cancelled. Cancel publishes no event.
- `course.enrollment.created` creates an invoice due 14 days from today. One message creates one invoice. A redelivered message can create a second invoice for the same enrollment.
- Wire that consumer now. Declare `dpp.events` (topic), queue `payment.q.enrollment-events`, and a binding for routing key `course.enrollment.created`. Point every service's `dpp-events-out` channel at `dpp.events`.
- The dead-letter exchange stays out of this plan.
- Reminders stay as designed: status `PENDING`, `reminderSentAt` null, `dueDate` on or before today plus 3 days. An `OVERDUE` invoice is not reminded.
- Overdue stays as designed: `dueDate` strictly before today and status `PENDING`. An invoice due today is not overdue.

## Steps

### 1. Module

- [x] Add `include("payment-service")` to [settings.gradle.kts](../settings.gradle.kts).
- [x] Add `build.gradle.kts` with the `dpp.quarkus-service` plugin, `dpp-commons`, MockK, and `implementation("io.quarkus:quarkus-scheduler")`.

### 2. Domain

Package `core.models`, `core.repository`, `core.exceptions`. No framework imports.

- [x] `InvoiceId`, `PaymentId`.
- [x] `InvoiceStatus`: `PENDING`, `PAID`, `OVERDUE`, `CANCELLED`.
- [x] `Invoice` (`studentId`, `enrollmentId`, `amount`, `dueDate`, `status`, `reminderSentAt`) and `Payment` (`invoiceId`, `amount`, `paidAt`, `method`). `method` stays a string.
- [x] `InvoiceRepository`: `save`, `findByInvoiceId`, `findByIdForUpdate`, `listByStudent`, `findDueReminders`, `markReminderSent`, `markOverdue`, `updateStatus`.
- [x] `PaymentRepository`: `save`, `sumPaidForInvoice`.
- [x] `InvoiceNotFoundException`, `InvoiceAlreadyPaidException`, `InvoiceCancelledException`, `OverpaymentException`.

### 3. Use cases

`@ApplicationScoped` on every use case. `@Transactional` on every mutating `execute`. `RecordPaymentUseCase` needs the transaction so the row lock is held.

- [x] `CreateInvoiceUseCase`. Saves status `PENDING` and publishes `payment.invoice.created` with `InvoiceCreatedPayload`.
- [x] `GenerateInvoiceOnEnrollmentUseCase`. Calls create with `studentId`, `enrollmentId`, `coursePrice`, and `LocalDate.now().plusDays(14)`.
- [x] `RecordPaymentUseCase`. Locks the invoice, rejects `CANCELLED` and `PAID`, rejects an amount that would exceed the balance, saves the payment, sets `PAID` when the total matches, and publishes `payment.received` with `PaymentReceivedPayload`.
- [x] `CancelInvoiceUseCase`. Rejects `PAID` and `CANCELLED`. Sets `CANCELLED`. Publishes nothing.
- [x] `ListInvoicesForStudentUseCase`.
- [x] `FindDueReminderInvoicesUseCase`. Publishes `payment.reminder.due` with `PaymentReminderDuePayload`, then marks the reminder sent.
- [x] `MarkOverdueInvoicesUseCase`. Publishes `payment.overdue` (`invoiceId`, `studentId`, `amount`, `dueDate`) for each invoice the repository marks overdue.

### 4. Persistence, messaging, and scheduling

- [x] `InvoiceJpaEntity` (`invoices`) and `PaymentJpaEntity` (`payments`), with `@Column` for `student_id`, `enrollment_id`, `due_date`, `reminder_sent_at`, `invoice_id`, `paid_at`.
- [x] `InvoiceMapper`, `PaymentMapper`.
- [x] `PanacheInvoiceRepository`. `findByIdForUpdate` uses `PESSIMISTIC_WRITE`. `markOverdue` updates `PENDING` rows with `dueDate < asOf` and returns those invoices. `sumPaidForInvoice` sums payment amounts, returning zero when there are none.
- [x] `RabbitMqEventPublisher` as `@Singleton` on channel `dpp-events-out`.
- [x] `EnrollmentCreatedListener` in `infrastructure.messaging`. `@Incoming("course-enrollment-events")` and `@RunOnVirtualThread`. It reads `EnrollmentCreatedPayload` (`enrollmentId`, `studentId`, `batchId`, `courseId`, `coursePrice`) and calls `GenerateInvoiceOnEnrollmentUseCase`. If Jackson drops the generic payload type, use a concrete envelope class instead of `EventEnvelope<EnrollmentCreatedPayload>`.
- [x] `PaymentReminderJob` in `infrastructure.scheduling`: `@Scheduled(every = "1h")` and `@RunOnVirtualThread`. It runs the reminder use case, then the overdue use case.

### 5. REST layer

Every endpoint uses `@RunOnVirtualThread` and maps DTOs in the resource. Base path `/api/v1/invoices`.

- [x] `POST` creates an invoice (201). Body: `studentId`, `enrollmentId`, `amount`, `dueDate`.
- [x] `GET` lists a student's invoices (`studentId`).
- [x] `DELETE /{id}` cancels (204).
- [x] `POST /{id}/payments` records a payment (201). Body: `amount`, `method`.
- [x] DTOs: `CreateInvoiceRequest`, `InvoiceResponse`, `RecordPaymentRequest`, `PaymentResponse`.
- [x] `PaymentExceptionMapper : BaseExceptionMapper()` with `@Provider`.

### 6. Database, config, and broker

- [x] `src/main/resources/db/migration/V1.0.0__Create_tables.sql` from design section 7.3, without a schema prefix:
  - `invoices`, `payments`
  - indexes `idx_invoices_student`, `idx_invoices_due`
- [x] `application.properties`:
  - `jdbc:postgresql://localhost:5432/payment` with `payment` / `payment123`
  - `quarkus.flyway.migrate-at-start=true`
  - `quarkus.http.port=8084`
  - `%test.quarkus.http.test-port=0`
  - outgoing `dpp-events-out` with `payment_user` / `PaymentPassword123` and exchange `dpp.events`
  - incoming `course-enrollment-events` on the same broker, queue `payment.q.enrollment-events`, routing key `course.enrollment.created`. Do not let the connector declare the exchange or queue if the broker user is not allowed to; the definitions file owns them.
- [x] Add to [local/rabbitmq/definitions.json](../local/rabbitmq/definitions.json): topic exchange `dpp.events`, queue `payment.q.enrollment-events`, binding for `course.enrollment.created`.
- [x] Set `mp.messaging.outgoing.dpp-events-out.exchange.name=dpp.events` on user-service, course-service, appointment-service, and test-service so their events use that exchange.
- [x] The running RabbitMQ volume will not reload `definitions.json`. Apply the same exchange, queue, and binding to the running broker.

### 7. Tests

JUnit 5 and MockK for use cases, the listener, and the resource. `@QuarkusTest` and `@TestTransaction` for repositories, against the local `payment` database.

- [x] Use case tests: create publishes `payment.invoice.created`; enrollment generation uses the course price and a due date 14 days out; a partial payment publishes `payment.received` and leaves the invoice unpaid; an exact payment sets `PAID`; overpayment, `PAID`, and `CANCELLED` publish nothing; cancel of `PAID` and `CANCELLED` changes nothing; cancel of `PENDING` sets `CANCELLED` and publishes nothing; reminder publishes `payment.reminder.due` and marks sent; overdue publishes `payment.overdue`.
- [x] Listener test: a `course.enrollment.created` payload is forwarded to `GenerateInvoiceOnEnrollmentUseCase`.
- [x] Resource tests: status codes, request fields forwarded to the use case, conflict and overpayment exceptions left to the mapper.
- [x] Repository tests: `sumPaidForInvoice` is zero then the payment total; `markOverdue` changes only `PENDING` rows due before today; the reminder query ignores sent, non-`PENDING`, and far-future invoices.
- [x] `./gradlew :payment-service:test`.

`./gradlew :payment-service:test` passed: 21 tests, 0 failures, against the local `payment` database.

The running RabbitMQ volume does not reload `definitions.json`. The topic exchange `dpp.events`, queue `payment.q.enrollment-events`, and the `course.enrollment.created` binding were applied through the management API on the running broker.

`markOverdue` is a bulk update, so Hibernate keeps the old row in the persistence context. The repository test clears that context before reading the status back.
