# Driving School Management Platform — System Design

Stack: Quarkus + Kotlin, Java virtual threads, RabbitMQ, PostgreSQL, Helm/Kubernetes.
Internal structure: **Clean Architecture** in every service (domain → application → infrastructure/presentation, dependencies point inward only).

## 1. Goals & Constraints

- Microservice architecture, but **cheap to run on day one** and able to scale later without re-architecting.
- Kotlin on Quarkus, using **virtual threads** (`@RunOnVirtualThread`) instead of the reactive stack, so we can write plain blocking JDBC/Hibernate code and still get high concurrency with low memory overhead.
- **Clean Architecture inside each service**, so business rules (registration rules, scheduling rules, reminder rules) live in plain Kotlin with zero Quarkus/JPA/RabbitMQ imports. Swapping Postgres for something else, or REST for GraphQL, or RabbitMQ for SQS, would only touch the `infrastructure` and `presentation` layers — the `domain` and `application` layers never change. This also makes the domain logic trivially unit-testable with no Quarkus test context needed.
- RabbitMQ as the backbone for all cross-service communication that isn't a direct synchronous read (notifications, reminders, cross-service state propagation).
- PostgreSQL as the system of record, schema-per-service from day one so services can be split onto separate DB instances later with zero code change (only a connection string change).
- Helm charts, one per service, deployed to a small Kubernetes cluster.

---

## 2. Service Catalog

| # | Service | Owns | Sync API | Publishes | Consumes |
|---|---|---|---|---|---|
| 1 | **identity-service** | Students, Teachers, Admins, Auth | Yes (REST) | `user.*` | — |
| 2 | **course-service** | Courses, Curriculum, Class batches, Enrollment | Yes (REST) | `course.*`, `class.*` | `user.student.registered` |
| 3 | **appointment-service** | 1:1 driving lesson bookings, instructor/vehicle availability | Yes (REST) | `appointment.*` | `class.enrollment.created` |
| 4 | **test-service** | Mock tests & official test scheduling | Yes (REST) | `test.*` | `class.enrollment.created` |
| 5 | **payment-service** | Fees, invoices, payment status | Yes (REST) | `payment.*` | `course.enrollment.created` |
| 6 | **notification-preference-service** | Per-user, per-event-type, per-channel notification preferences | Yes (REST) | `preference.*` | `user.student.registered`, `user.teacher.registered` |
| 7 | **notification-service** | Notification templates, delivery (email/SMS/push), delivery log | Thin REST (query delivery history) | `notification.*` | everything in §6 |
| 8 | **api-gateway** | Routing, auth termination, rate limiting | N/A (edge) | — | — |

Boundary rationale is unchanged from before: Class/enrollment stays with Course (1:1 lifecycle), Test scheduling is split from Appointment (different lifecycle: results, external test-center calendars), Notification Preference is split from Notification delivery (CRUD vs. background event processing — different read/write and scaling profiles).

---

## 3. Clean Architecture Layering (applies to every service)

Every service — `identity-service`, `course-service`, `appointment-service`, `test-service`, `payment-service`, `notification-preference-service`, `notification-service` — uses the same four layers and the same dependency rule: **outer layers depend on inner layers, never the reverse.**

```
com.drivingschool.<service>
├── domain/
│   ├── model/          # pure Kotlin classes — no @Entity, no @Path, no Quarkus imports at all
│   ├── repository/     # port interfaces, e.g. StudentRepository — domain owns the contract
│   ├── event/           # domain event port, e.g. DomainEventPublisher interface
│   └── exception/       # domain-specific exceptions (StudentAlreadyRegisteredException, etc.)
├── application/
│   └── usecase/         # one class per use case/interactor, orchestrates domain model + ports
├── infrastructure/
│   ├── persistence/
│   │   ├── entity/       # JPA/Panache entities — the ONLY place persistence annotations live
│   │   ├── mapper/       # domain model <-> JPA entity mappers
│   │   └── repository/   # implements the domain repository port using Panache
│   └── messaging/
│       ├── publisher/    # implements DomainEventPublisher using RabbitMQ (Reactive Messaging emitter)
│       └── consumer/     # @Incoming listeners; translate inbound AMQP messages into use-case calls
└── presentation/
    └── rest/
        ├── resource/     # JAX-RS resources — thin, just: parse request -> call use case -> map response
        └── dto/          # request/response DTOs (never reuse the domain model as the wire format)
```

**Dependency rule in practice:**
- `domain` has no dependency on anything else in the project or on Quarkus/Jakarta — it's a plain Kotlin module conceptually (physically it's a package inside the service module, but nothing in it imports `jakarta.*`, `io.quarkus.*`, or `org.hibernate.*`).
- `application` depends only on `domain` (models + repository/event ports). It has no idea Postgres or RabbitMQ exist.
- `infrastructure` depends on `domain` (to implement its ports) and on Quarkus/Panache/RabbitMQ. This is where all the framework code lives.
- `presentation` depends on `application` (calls use cases) and defines its own DTOs — it never leaks JPA entities or domain models directly onto the wire.
- `@RunOnVirtualThread` is applied at the edges only — on `presentation.rest` resource methods and `infrastructure.messaging.consumer` listener methods — never inside `domain`/`application`, which stay framework-agnostic and synchronous by nature.

**Ports summary (in `ds-commons`, shared across services):**

```kotlin
// ds-commons — the one interface every service's infrastructure layer implements
interface DomainEventPublisher {
    fun publish(envelope: EventEnvelope<*>)
}
```

Each service provides exactly one implementation of this (`infrastructure.messaging.publisher.RabbitMqEventPublisher`) backed by a Quarkus Reactive Messaging `@Channel` emitter, wired via CDI (`@ApplicationScoped`, injected into use cases through the `DomainEventPublisher` interface — a use case never imports RabbitMQ, only this interface).

---

## 4. Service Designs

### 4.1 identity-service — fully worked example

**Domain layer** — plain Kotlin, no annotations:

```kotlin
// domain/model/Student.kt
data class Student(
    val id: StudentId,
    val email: String,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val dateOfBirth: LocalDate?,
    val guardianContact: String?,
    val licenseStage: LicenseStage = LicenseStage.LEARNER,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val createdAt: Instant
) {
    fun requiresGuardianContact(): Boolean =
        dateOfBirth != null && Period.between(dateOfBirth, LocalDate.now()).years < 18
}

@JvmInline value class StudentId(val value: UUID)
enum class LicenseStage { LEARNER, PROVISIONAL, FULL }
enum class AccountStatus { ACTIVE, SUSPENDED, ARCHIVED }

// domain/repository/StudentRepository.kt — the port; domain defines the contract
interface StudentRepository {
    fun save(student: Student): Student
    fun findById(id: StudentId): Student?
    fun existsByEmail(email: String): Boolean
    fun search(query: String?, page: Int, size: Int): List<Student>
}

// domain/exception/DomainExceptions.kt
class EmailAlreadyRegisteredException(email: String) : RuntimeException("Email already registered: $email")
class MinorMissingGuardianContactException : RuntimeException("Guardian contact is required for students under 18")
```

**Application layer** — orchestrates domain + ports, no framework imports:

```kotlin
// application/usecase/RegisterStudentUseCase.kt
class RegisterStudentUseCase(
    private val studentRepository: StudentRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(command: RegisterStudentCommand): Student {
        if (studentRepository.existsByEmail(command.email)) {
            throw EmailAlreadyRegisteredException(command.email)
        }

        val student = Student(
            id = StudentId(UUID.randomUUID()),
            email = command.email,
            firstName = command.firstName,
            lastName = command.lastName,
            phone = command.phone,
            dateOfBirth = command.dateOfBirth,
            guardianContact = command.guardianContact,
            createdAt = Instant.now()
        )

        if (student.requiresGuardianContact() && student.guardianContact.isNullOrBlank()) {
            throw MinorMissingGuardianContactException()
        }

        val saved = studentRepository.save(student)

        eventPublisher.publish(
            EventEnvelope(
                eventType = "user.student.registered",
                source = "identity-service",
                payload = StudentRegisteredPayload(saved.id.value, saved.email, saved.firstName, saved.lastName)
            )
        )
        return saved
    }
}

data class RegisterStudentCommand(
    val email: String, val firstName: String, val lastName: String,
    val phone: String?, val dateOfBirth: LocalDate?, val guardianContact: String?
)
```

Other use cases in this service, same shape: `GetStudentUseCase`, `SearchStudentsUseCase`, `UpdateStudentProfileUseCase`, `RegisterInstructorUseCase`, `UpdateAccountStatusUseCase`, `AuthenticateUserUseCase`.

**Infrastructure layer:**

```kotlin
// infrastructure/persistence/entity/StudentJpaEntity.kt
@Entity
@Table(name = "students", schema = "identity_schema")
class StudentJpaEntity : PanacheEntityBase {
    @Id lateinit var id: UUID
    lateinit var email: String
    lateinit var firstName: String
    lateinit var lastName: String
    var phone: String? = null
    var dateOfBirth: LocalDate? = null
    var guardianContact: String? = null
    @Enumerated(EnumType.STRING) lateinit var licenseStage: LicenseStage
    @Enumerated(EnumType.STRING) lateinit var status: AccountStatus
    lateinit var createdAt: Instant
}

// infrastructure/persistence/mapper/StudentMapper.kt
object StudentMapper {
    fun toDomain(e: StudentJpaEntity) = Student(
        StudentId(e.id), e.email, e.firstName, e.lastName, e.phone,
        e.dateOfBirth, e.guardianContact, e.licenseStage, e.status, e.createdAt
    )
    fun toEntity(s: Student) = StudentJpaEntity().apply {
        id = s.id.value; email = s.email; firstName = s.firstName; lastName = s.lastName
        phone = s.phone; dateOfBirth = s.dateOfBirth; guardianContact = s.guardianContact
        licenseStage = s.licenseStage; status = s.status; createdAt = s.createdAt
    }
}

// infrastructure/persistence/repository/PanacheStudentRepository.kt — implements the domain port
@ApplicationScoped
class PanacheStudentRepository : StudentRepository, PanacheRepositoryBase<StudentJpaEntity, UUID> {
    override fun save(student: Student): Student {
        val entity = StudentMapper.toEntity(student)
        persist(entity)
        return StudentMapper.toDomain(entity)
    }
    override fun findById(id: StudentId): Student? =
        findByIdOptional(id.value).map(StudentMapper::toDomain).orElse(null)
    override fun existsByEmail(email: String): Boolean =
        count("email", email) > 0
    override fun search(query: String?, page: Int, size: Int): List<Student> =
        (if (query == null) findAll() else find("lower(firstName) like ?1 or lower(lastName) like ?1", "%${query.lowercase()}%"))
            .page(page, size).list().map(StudentMapper::toDomain)
}

// infrastructure/messaging/publisher/RabbitMqEventPublisher.kt — implements DomainEventPublisher
@ApplicationScoped
class RabbitMqEventPublisher(
    @Channel("ds-events-out") private val emitter: Emitter<EventEnvelope<*>>
) : DomainEventPublisher {
    override fun publish(envelope: EventEnvelope<*>) {
        emitter.send(Message.of(envelope).addMetadata(
            OutgoingRabbitMQMetadata.builder().withRoutingKey(envelope.eventType).build()
        ))
    }
}

// CDI wiring — producer method so use cases can be injected without knowing the impl class
@ApplicationScoped
class UseCaseProducers(
    private val studentRepository: StudentRepository,
    private val eventPublisher: DomainEventPublisher
) {
    @Produces fun registerStudentUseCase() = RegisterStudentUseCase(studentRepository, eventPublisher)
}
```

**Presentation layer:**

```kotlin
// presentation/rest/dto/StudentDtos.kt
data class RegisterStudentRequest(
    val email: String, val firstName: String, val lastName: String,
    val phone: String?, val dateOfBirth: LocalDate?, val guardianContact: String?
)
data class StudentResponse(
    val id: UUID, val email: String, val licenseStage: String, val createdAt: Instant
)

// presentation/rest/resource/StudentResource.kt — thin: no business logic
@Path("/api/v1/students")
class StudentResource(
    private val registerStudentUseCase: RegisterStudentUseCase,
    private val getStudentUseCase: GetStudentUseCase
) {
    @POST
    @RunOnVirtualThread
    fun register(request: RegisterStudentRequest): Response {
        val student = registerStudentUseCase.execute(
            RegisterStudentCommand(request.email, request.firstName, request.lastName,
                request.phone, request.dateOfBirth, request.guardianContact)
        )
        return Response.status(201).entity(toResponse(student)).build()
    }

    @GET @Path("/{id}")
    @RunOnVirtualThread
    fun get(@PathParam("id") id: UUID): StudentResponse =
        toResponse(getStudentUseCase.execute(StudentId(id)))

    private fun toResponse(s: Student) = StudentResponse(s.id.value, s.email, s.licenseStage.name, s.createdAt)
}

// A JAX-RS ExceptionMapper (also presentation layer) translates domain exceptions to ProblemDetail:
@Provider
class DomainExceptionMapper : ExceptionMapper<RuntimeException> {
    override fun toResponse(e: RuntimeException): Response = when (e) {
        is EmailAlreadyRegisteredException -> problem(409, e.message)
        is MinorMissingGuardianContactException -> problem(422, e.message)
        else -> problem(500, "Unexpected error")
    }
    private fun problem(status: Int, detail: String?) =
        Response.status(status).entity(ProblemDetail(title = "Error", status = status, detail = detail)).build()
}
```

**Tables** (unchanged from the physical data model): `students`, `instructors` in schema `identity_schema`.

**REST API**

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/v1/auth/login` | public | Authenticate, returns JWT |
| POST | `/api/v1/students` | ADMIN | Register a new student |
| POST | `/api/v1/instructors` | ADMIN | Register a new teacher/instructor |
| GET | `/api/v1/students/{id}` | ADMIN, INSTRUCTOR, self | Get student profile |
| GET | `/api/v1/students` | ADMIN, INSTRUCTOR | List/search students (paged) |
| PUT | `/api/v1/students/{id}` | ADMIN, self | Update student profile |
| GET | `/api/v1/instructors/{id}` | ADMIN, self | Get instructor profile |
| GET | `/api/v1/instructors` | ADMIN | List instructors |
| PUT | `/api/v1/instructors/{id}` | ADMIN, self | Update instructor profile |
| PATCH | `/api/v1/users/{id}/status` | ADMIN | Suspend/reactivate/archive a user |

**Publishes:** `user.student.registered`, `user.instructor.registered`, `user.status.changed`
**Consumes:** none

**Testability payoff:** `RegisterStudentUseCase` is tested with a fake in-memory `StudentRepository` and a no-op `DomainEventPublisher` — no `@QuarkusTest`, no database, no RabbitMQ, sub-millisecond unit tests for every business rule (guardian-contact-for-minors, duplicate-email, etc.).

---

### 4.2 course-service

Same four layers; shown condensed since the pattern is now established.

```
domain/model/        Course, ClassBatch, ClassSession, Enrollment (pure Kotlin, value objects for IDs)
domain/repository/   CourseRepository, ClassBatchRepository, EnrollmentRepository (ports)
domain/exception/    BatchAtCapacityException, AlreadyEnrolledException
application/usecase/ CreateCourseUseCase, CreateClassBatchUseCase, ScheduleSessionUseCase,
                      RescheduleSessionUseCase, EnrollStudentUseCase, WithdrawEnrollmentUseCase,
                      ListEnrollmentsForStudentUseCase
infrastructure/      CourseJpaEntity/ClassBatchJpaEntity/.../ mappers, Panache repository impls,
                      RabbitMqEventPublisher (same shared pattern as 4.1)
presentation/        CourseResource, BatchResource, EnrollmentResource + DTOs
```

`EnrollStudentUseCase` is the interesting one — the capacity check is a **domain rule**, not a SQL detail:

```kotlin
class EnrollStudentUseCase(
    private val batchRepository: ClassBatchRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(command: EnrollStudentCommand): Enrollment {
        val batch = batchRepository.findByIdForUpdate(command.batchId)   // row-lock, see infra note below
            ?: throw BatchNotFoundException(command.batchId)

        val currentCount = enrollmentRepository.countActiveForBatch(batch.id)
        if (currentCount >= batch.capacity) throw BatchAtCapacityException(batch.id)
        if (enrollmentRepository.existsActive(command.studentId, batch.id)) {
            throw AlreadyEnrolledException(command.studentId, batch.id)
        }

        val enrollment = enrollmentRepository.save(Enrollment.create(command.studentId, batch.id))
        eventPublisher.publish(EventEnvelope(
            eventType = "course.enrollment.created", source = "course-service",
            payload = EnrollmentCreatedPayload(enrollment.id.value, command.studentId.value, batch.id.value, batch.courseId.value)
        ))
        return enrollment
    }
}
```

`findByIdForUpdate` is a domain-port method whose *infrastructure* implementation issues `SELECT ... FOR UPDATE` (Panache: `find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult()`) — the use case only knows "give me the batch in a way that's safe for concurrent enrollment," not that this is implemented with a Postgres row lock. That SQL detail is exactly the kind of thing Clean Architecture keeps out of `application`.

REST API, event catalog, and table names are unchanged from the original design (see §6 for the full event routing table).

---

### 4.3 appointment-service

```
domain/model/        Appointment, AvailabilitySlot (pure Kotlin)
domain/repository/   AppointmentRepository, AvailabilityRepository
application/usecase/ BookAppointmentUseCase, RescheduleAppointmentUseCase, CancelAppointmentUseCase,
                      CompleteAppointmentUseCase, DefineAvailabilityUseCase,
                      FindDueReminderAppointmentsUseCase   <- driven by a scheduler adapter, not REST
infrastructure/      AppointmentJpaEntity, AvailabilitySlotJpaEntity, mappers, Panache impls,
                      RabbitMqEventPublisher,
                      infrastructure/scheduling/AppointmentReminderJob.kt  (Quarkus @Scheduled)
presentation/        AppointmentResource, AvailabilityResource + DTOs
```

The reminder job is a good example of a **second, non-REST entry point into the same use case layer** — this is exactly what Clean Architecture's "any delivery mechanism can call the use case" principle buys you:

```kotlin
// infrastructure/scheduling/AppointmentReminderJob.kt
@ApplicationScoped
class AppointmentReminderJob(
    private val findDueReminderAppointmentsUseCase: FindDueReminderAppointmentsUseCase
) {
    @Scheduled(every = "15m")
    @RunOnVirtualThread
    fun run() {
        findDueReminderAppointmentsUseCase.execute()
    }
}

// application/usecase/FindDueReminderAppointmentsUseCase.kt
class FindDueReminderAppointmentsUseCase(
    private val appointmentRepository: AppointmentRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute() {
        val due = appointmentRepository.findStartingWithinWithNoReminderSent(withinHours = 24)
        due.forEach { appointment ->
            eventPublisher.publish(EventEnvelope(
                eventType = "appointment.reminder.due", source = "appointment-service",
                payload = AppointmentReminderDuePayload(appointment.id.value, appointment.studentId.value,
                    appointment.instructorId.value, appointment.startAt, appointment.pickupLocation)
            ))
            appointmentRepository.markReminderSent(appointment.id)
        }
    }
}
```

REST API and event catalog unchanged from the original design.

---

### 4.4 test-service

```
domain/model/        TestSchedule (with TestType, TestScheduleStatus, TestResult as domain enums)
domain/repository/   TestScheduleRepository
application/usecase/ ScheduleTestUseCase, RescheduleTestUseCase, CancelTestUseCase,
                      RecordTestResultUseCase, FindDueReminderTestsUseCase
infrastructure/      TestScheduleJpaEntity, mapper, Panache impl, RabbitMqEventPublisher,
                      TestReminderJob (@Scheduled, same shape as 4.3)
presentation/        TestResource + DTOs
```

REST API and event catalog unchanged from the original design.

---

### 4.5 payment-service

```
domain/model/        Invoice, Payment
domain/repository/   InvoiceRepository, PaymentRepository
domain/exception/    InvoiceAlreadyPaidException, OverpaymentException
application/usecase/ CreateInvoiceUseCase, RecordPaymentUseCase, CancelInvoiceUseCase,
                      GenerateInvoiceOnEnrollmentUseCase,   <- triggered by the RabbitMQ consumer, not REST
                      FindDueReminderInvoicesUseCase, MarkOverdueInvoicesUseCase
infrastructure/      InvoiceJpaEntity, PaymentJpaEntity, mappers, Panache impls, RabbitMqEventPublisher,
                      infrastructure/messaging/consumer/EnrollmentCreatedListener.kt,
                      PaymentReminderJob (@Scheduled)
presentation/        InvoiceResource + DTOs
```

`GenerateInvoiceOnEnrollmentUseCase` is called from an inbound RabbitMQ adapter, which is exactly the same pattern as the reminder job being called from a scheduler adapter — different trigger, same use-case layer, zero duplicated business logic:

```kotlin
// infrastructure/messaging/consumer/EnrollmentCreatedListener.kt
@ApplicationScoped
class EnrollmentCreatedListener(
    private val generateInvoiceOnEnrollmentUseCase: GenerateInvoiceOnEnrollmentUseCase
) {
    @Incoming("course-enrollment-events")
    @RunOnVirtualThread
    fun onMessage(envelope: EventEnvelope<EnrollmentCreatedPayload>) {
        generateInvoiceOnEnrollmentUseCase.execute(envelope.payload)
    }
}
```

REST API and event catalog unchanged from the original design.

---

### 4.6 notification-preference-service

```
domain/model/        NotificationPreference (userId, eventType, channel, enabled, quiet hours)
domain/repository/   NotificationPreferenceRepository
application/usecase/ UpsertPreferencesUseCase, GetPreferenceMatrixUseCase, TogglePreferenceUseCase,
                      SeedDefaultPreferencesUseCase   <- triggered by the RabbitMQ consumer below
infrastructure/      NotificationPreferenceJpaEntity, mapper, Panache impl, RabbitMqEventPublisher,
                      infrastructure/messaging/consumer/UserRegisteredListener.kt
presentation/        PreferenceResource + DTOs
```

Every time `UpsertPreferencesUseCase`/`TogglePreferenceUseCase` changes a row, it publishes `preference.updated` through the same `DomainEventPublisher` port — notification-service is the consumer (see 4.7 and §6).

REST API unchanged from the original design.

---

### 4.7 notification-service

This is the service with the most interesting domain logic — deciding *whether* and *how* to notify someone — so it benefits the most from being pulled out of framework code.

```
domain/model/         NotificationLogEntry, PreferenceCacheEntry, NotificationTemplate
domain/repository/    NotificationLogRepository, PreferenceCacheRepository, TemplateRepository
application/usecase/  ProcessDomainEventUseCase   <- the core orchestrator, described below
                       UpdatePreferenceCacheUseCase   <- handles inbound preference.updated events
infrastructure/
  messaging/consumer/  DomainEventListener.kt (binds all the notif.q.* queues), PreferenceUpdatedListener.kt
  notification/        EmailSender, SmsSender, PushSender (adapters behind a shared port, see below)
  persistence/          JPA entities/mappers/Panache impls for the three repositories above
presentation/rest/     NotificationHistoryResource, TemplateResource + DTOs (read-only/admin)
```

The channel-sending abstraction is itself a port, so `application` never knows whether it's calling SES, Twilio, or FCM:

```kotlin
// domain/repository/NotificationSender.kt — a port even though it's not persistence
interface NotificationSender {
    fun send(channel: NotificationChannel, recipient: String, subject: String?, body: String): SendResult
}

// application/usecase/ProcessDomainEventUseCase.kt
class ProcessDomainEventUseCase(
    private val preferenceCacheRepository: PreferenceCacheRepository,
    private val templateRepository: TemplateRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val notificationSender: NotificationSender,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(sourceEventId: UUID, eventType: NotificationEventType, recipients: List<Recipient>) {
        recipients.forEach { recipient ->
            NotificationChannel.entries.forEach { channel ->
                if (notificationLogRepository.existsFor(sourceEventId, channel)) return@forEach // idempotency
                val enabled = preferenceCacheRepository.isEnabled(recipient.userId, eventType, channel)
                if (!enabled) {
                    notificationLogRepository.save(NotificationLogEntry.suppressed(recipient.userId, eventType, channel))
                    return@forEach
                }
                val template = templateRepository.find(eventType, channel) ?: return@forEach
                val result = notificationSender.send(channel, recipient.address, template.render(recipient), template.body)
                notificationLogRepository.save(NotificationLogEntry.from(result, recipient.userId, eventType, channel))
                eventPublisher.publish(EventEnvelope(
                    eventType = if (result.success) "notification.sent" else "notification.failed",
                    source = "notification-service",
                    payload = NotificationOutcomePayload(recipient.userId.value, eventType.name, channel.name, result.providerMessageId, result.error)
                ))
            }
        }
    }
}
```

The inbound RabbitMQ adapter just maps an AMQP message to this use case's input — routing-key-to-`NotificationEventType`/recipient-extraction logic lives in the adapter (it's infrastructure-specific plumbing), not in the use case:

```kotlin
// infrastructure/messaging/consumer/DomainEventListener.kt
@ApplicationScoped
class DomainEventListener(private val processDomainEventUseCase: ProcessDomainEventUseCase) {

    @Incoming("appointment-events")
    @RunOnVirtualThread
    fun onAppointmentEvent(envelope: EventEnvelope<Map<String, Any>>) {
        val (eventType, recipients) = AppointmentEventMapping.map(envelope) // infra-only mapping helper
        processDomainEventUseCase.execute(envelope.eventId, eventType, recipients)
    }

    // onClassEvent, onTestEvent, onPaymentEvent, onUserEvent — same shape, one per bound queue
}
```

REST API, routing/queue tables unchanged from the original design (see §6).

---

## 5. API Gateway

Unchanged from the original design — a thin edge layer (Kong or Traefik) handling TLS termination, JWT validation, rate limiting, and path routing. It sits entirely outside the Clean Architecture discussion since it isn't running business logic, just routing:

```
/api/v1/students/**      -> identity-service
/api/v1/instructors/**   -> identity-service
/api/v1/auth/**          -> identity-service
/api/v1/courses/**       -> course-service
/api/v1/batches/**       -> course-service
/api/v1/enrollments/**   -> course-service
/api/v1/availability/**  -> appointment-service
/api/v1/appointments/**  -> appointment-service
/api/v1/tests/**         -> test-service
/api/v1/invoices/**      -> payment-service
/api/v1/users/*/preferences/** -> notification-preference-service
/api/v1/notifications/** -> notification-service
/api/v1/templates/**     -> notification-service
```

---

## 6. Eventing / RabbitMQ Topology

Unchanged in shape from the original design — this is a cross-cutting concern that Clean Architecture deliberately keeps at the edges (the `infrastructure.messaging` package of each service), never inside `domain`/`application`.

### 6.1 Exchanges

| Exchange | Type | Purpose |
|---|---|---|
| `ds.events` | topic | All domain events published by business services |
| `ds.dlx` | fanout | Dead-letter target for any queue whose messages are rejected/expired |

### 6.2 Routing key catalog

| Routing key | Publisher | Payload summary |
|---|---|---|
| `user.student.registered` | identity-service | studentId, email, firstName, lastName |
| `user.instructor.registered` | identity-service | instructorId, email, firstName, lastName |
| `user.status.changed` | identity-service | userId, oldStatus, newStatus |
| `class.batch.created` | course-service | batchId, courseId, instructorId, startDate, endDate |
| `class.session.scheduled` | course-service | sessionId, batchId, sessionDate, startTime, endTime |
| `class.session.rescheduled` | course-service | sessionId, oldStart, newStart |
| `class.session.cancelled` | course-service | sessionId, batchId |
| `course.enrollment.created` | course-service | enrollmentId, studentId, batchId, courseId |
| `course.enrollment.withdrawn` | course-service | enrollmentId, studentId, batchId |
| `appointment.created` | appointment-service | appointmentId, studentId, instructorId, startAt |
| `appointment.rescheduled` | appointment-service | appointmentId, oldStart, newStart |
| `appointment.cancelled` | appointment-service | appointmentId, studentId |
| `appointment.reminder.due` | appointment-service | appointmentId, studentId, instructorId, startAt |
| `test.scheduled` | test-service | testId, studentId, testType, scheduledAt |
| `test.rescheduled` | test-service | testId, oldTime, newTime |
| `test.cancelled` | test-service | testId, studentId |
| `test.reminder.due` | test-service | testId, studentId, testType, scheduledAt |
| `test.result.published` | test-service | testId, studentId, result |
| `payment.invoice.created` | payment-service | invoiceId, studentId, amount, dueDate |
| `payment.received` | payment-service | invoiceId, studentId, amount, paidAt |
| `payment.reminder.due` | payment-service | invoiceId, studentId, amount, dueDate |
| `payment.overdue` | payment-service | invoiceId, studentId, amount, dueDate |
| `preference.updated` | notification-preference-service | userId, eventType, channel, enabled |
| `notification.sent` | notification-service | userId, eventType, channel, providerMessageId |
| `notification.failed` | notification-service | userId, eventType, channel, errorMessage |

### 6.3 Queues & bindings

| Queue | Bound to routing pattern(s) | Consumer |
|---|---|---|
| `notif.q.user-events` | `user.#` | notification-service, notification-preference-service |
| `notif.q.class-events` | `class.#`, `course.enrollment.#` | notification-service |
| `notif.q.appointment-events` | `appointment.#` | notification-service |
| `notif.q.test-events` | `test.#` | notification-service |
| `notif.q.payment-events` | `payment.#` | notification-service |
| `notif.q.preference-updates` | `preference.updated` | notification-service (keeps `preference_cache` in sync) |
| `payment.q.enrollment-events` | `course.enrollment.created` | payment-service (auto-invoice) |

Each queue: `x-dead-letter-exchange: ds.dlx`, `x-message-ttl` tuned per queue, with a `ds.dlq.catch-all` queue for anything that repeatedly fails.

### 6.4 Event object specification

```kotlin
// ds-commons
data class EventEnvelope<T>(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val occurredAt: Instant = Instant.now(),
    val version: String = "1.0",
    val source: String,
    val correlationId: String? = null,
    val payload: T
)
```

Wire format example (unchanged):
```json
{
  "eventId": "b1e2-...",
  "eventType": "appointment.reminder.due",
  "occurredAt": "2026-08-03T14:00:00Z",
  "version": "1.0",
  "source": "appointment-service",
  "correlationId": null,
  "payload": {
    "appointmentId": "ap55-...",
    "studentId": "b3c1-...",
    "instructorId": "9a02-...",
    "startAt": "2026-08-04T14:00:00Z",
    "pickupLocation": "123 Main St"
  }
}
```

---

## 7. Data Storage Strategy

Unchanged: one PostgreSQL instance to start, schema-per-service (`identity_schema`, `course_schema`, `appointment_schema`, `test_schema`, `payment_schema`, `notification_pref_schema`, `notification_schema`), each service connecting with a role scoped to only its own schema. Clean Architecture reinforces this further at the code level: a service's `infrastructure.persistence` package only ever contains JPA entities and repository implementations for *that service's own* domain model — there is no mechanism in the layering for one service's code to reach into another schema, since there's no shared JPA `EntityManager` across service boundaries at all.

---

## 8. Deployment — Helm

Unchanged from the original design: one Helm chart per service, umbrella chart, small starting resource requests (`100m`/`256Mi`), HPA on CPU for request-driven services, **KEDA** scaling notification-service's consumers on RabbitMQ queue depth rather than CPU. See the earlier discussion for full chart layout and `ScaledObject` example — none of this changes based on internal service architecture, since Helm only cares about the container image and its exposed port/health checks, not what's inside.

---

## 9. Security & Cross-Cutting

- **AuthN/AuthZ:** OIDC via Keycloak; each service validates the JWT via `quarkus-oidc` in the `presentation` layer (role/ownership checks belong in the REST resource or a `@Provider` filter, *not* inside use cases — a use case shouldn't need to know "who is allowed to call me," only "is this command valid business-wise").
- **Observability:** `quarkus-micrometer` + Prometheus/Grafana, `quarkus-opentelemetry` for tracing, correlated via `EventEnvelope.correlationId`.
- **Virtual threads:** applied at the `presentation.rest` and `infrastructure.messaging.consumer`/`infrastructure.scheduling` boundaries only — the exact seams where Clean Architecture already draws a line between "framework entry point" and "business logic," which makes this an easy rule to enforce in code review (`@RunOnVirtualThread` should never appear inside `domain` or `application`).
- **Unit vs. integration tests:** `domain`/`application` get plain JUnit5 + fake in-memory repository/publisher implementations (fast, no Quarkus). `infrastructure` gets `@QuarkusTest` + Testcontainers (real Postgres, real RabbitMQ via `quarkus-test-devservices`) to verify the Panache repository/mapper/messaging adapters actually satisfy the domain ports correctly.

---

## 10. Rollout Plan

Unchanged: Phase 1 (identity, course, appointment, notification-preference, notification/email-only) → Phase 2 (add test-service, payment-service, SMS/push, KEDA scaling) → Phase 3 (split hot schemas onto dedicated Postgres instances, cluster RabbitMQ, harden the gateway tier). The Clean Architecture layering doesn't change this plan — it only affects how each service is organized internally, not the order you build/ship them in.
