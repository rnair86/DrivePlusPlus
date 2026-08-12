# Driving School Management Platform — System Design

Stack: Quarkus + Kotlin, Java virtual threads, RabbitMQ, PostgreSQL, Helm/Kubernetes.
Internal structure: **Clean Architecture** in every service (domain → application → infrastructure/presentation, dependencies point inward only).

## 1. Goals & Constraints

- Microservice architecture, cheap to run on day one, scalable later without re-architecting.
- Kotlin on Quarkus, using **virtual threads** (`@RunOnVirtualThread`) instead of the reactive stack — plain blocking JDBC/Hibernate, high concurrency, low memory overhead.
- **Clean Architecture inside each service** — `domain`/`application` have zero framework imports and are unit-testable without Quarkus.
- RabbitMQ for all cross-service communication that isn't a direct synchronous read.
- PostgreSQL, schema-per-service from day one.
- Helm charts, one per service.

---

## 2. Service Catalog

| # | Service | Owns | Sync API | Publishes | Consumes |
|---|---|---|---|---|---|
| 1 | **identity-service** | Users, student/instructor profiles, auth | Yes | `user.*` | — |
| 2 | **course-service** | Courses, class batches/sessions, enrollment | Yes | `course.*`, `class.*` | `user.student.registered` |
| 3 | **appointment-service** | 1:1 lesson bookings, instructor availability | Yes | `appointment.*` | `course.enrollment.created` (reserved) |
| 4 | **test-service** | Mock/official test scheduling & results | Yes | `test.*` | `course.enrollment.created` (reserved) |
| 5 | **payment-service** | Invoices, payments | Yes | `payment.*` | `course.enrollment.created` |
| 6 | **notification-preference-service** | Per-user/event/channel preferences | Yes | `preference.*` | `user.student.registered`, `user.instructor.registered` |
| 7 | **notification-service** | Templates, delivery, delivery log | Thin REST (history) | `notification.*` | all of §6 |
| 8 | **api-gateway** | Routing, auth termination, rate limiting | N/A (edge) | — | — |

---

## 3. `ds-commons` — Shared Kotlin Module (complete class list)

Every class below lives in `ds-commons` and is depended on by all 7 services. Nothing here imports a specific service's domain types — this module only holds truly cross-cutting building blocks.

```kotlin
// package com.drivingschool.commons.event
data class EventEnvelope<T>(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val occurredAt: Instant = Instant.now(),
    val version: String = "1.0",
    val source: String,
    val correlationId: String? = null,
    val payload: T
)

interface DomainEventPublisher {
    fun publish(envelope: EventEnvelope<*>)
}
```

```kotlin
// package com.drivingschool.commons.messaging
object RabbitTopology {
    const val EVENTS_EXCHANGE = "ds.events"
    const val DLX = "ds.dlx"
}

// Shared publisher base — every service's publisher is a 3-line subclass of this
abstract class AbstractRabbitMqEventPublisher(
    private val emitter: Emitter<EventEnvelope<*>>
) : DomainEventPublisher {
    override fun publish(envelope: EventEnvelope<*>) {
        emitter.send(
            Message.of(envelope).addMetadata(
                OutgoingRabbitMQMetadata.builder().withRoutingKey(envelope.eventType).build()
            )
        )
    }
}
```

```kotlin
// package com.drivingschool.commons.error
data class ProblemDetail(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<FieldError> = emptyList()
)
data class FieldError(val field: String, val message: String)

// Every service-specific exception extends one of these three, never plain RuntimeException
abstract class DomainException(message: String) : RuntimeException(message)
abstract class NotFoundException(message: String) : DomainException(message)
abstract class ConflictException(message: String) : DomainException(message)
abstract class ValidationException(message: String) : DomainException(message)

// Shared JAX-RS mapper base — every service registers a 3-line subclass as its @Provider
abstract class BaseExceptionMapper : ExceptionMapper<RuntimeException> {
    override fun toResponse(e: RuntimeException): Response = when (e) {
        is NotFoundException -> problem(404, e.message)
        is ConflictException -> problem(409, e.message)
        is ValidationException -> problem(422, e.message)
        else -> problem(500, "Unexpected error")
    }
    private fun problem(status: Int, detail: String?): Response =
        Response.status(status).entity(ProblemDetail(title = "Error", status = status, detail = detail)).build()
}
```

```kotlin
// package com.drivingschool.commons.paging
data class PageRequest(val page: Int = 0, val size: Int = 20)
data class PageResult<T>(val items: List<T>, val page: Int, val size: Int, val totalElements: Long)
```

```kotlin
// package com.drivingschool.commons.notification
enum class NotificationChannel { EMAIL, SMS, PUSH }

enum class NotificationEventType {
    STUDENT_REGISTERED, INSTRUCTOR_REGISTERED,
    CLASS_SESSION_SCHEDULED, CLASS_SESSION_RESCHEDULED, CLASS_SESSION_CANCELLED,
    APPOINTMENT_CREATED, APPOINTMENT_REMINDER, APPOINTMENT_CANCELLED,
    TEST_SCHEDULED, TEST_REMINDER, TEST_RESULT_PUBLISHED,
    PAYMENT_DUE, PAYMENT_OVERDUE, PAYMENT_RECEIVED
}
```

```kotlin
// package com.drivingschool.commons.persistence
@MappedSuperclass
abstract class BaseJpaEntity : PanacheEntityBase {
    @Id lateinit var id: UUID
}
```

---

## 4. Service Designs — full class lists

Package skeleton, identical in every service:
```
com.drivingschool.<service>
├── domain/{model,repository,exception}/
├── application/usecase/
├── infrastructure/{persistence/{entity,mapper,repository}, messaging/{publisher,consumer}, scheduling}/
└── presentation/rest/{resource,dto}/
```

### 4.1 identity-service

**Domain — model**
```kotlin
@JvmInline value class UserId(val value: UUID)
enum class UserRole { ADMIN, INSTRUCTOR, STUDENT }
enum class AccountStatus { ACTIVE, SUSPENDED, ARCHIVED }
enum class LicenseStage { LEARNER, PROVISIONAL, FULL }

data class UserAccount(
    val id: UserId, val email: String, val passwordHash: String,
    val role: UserRole, val status: AccountStatus = AccountStatus.ACTIVE, val createdAt: Instant
)

data class StudentProfile(
    val userId: UserId, val firstName: String, val lastName: String, val phone: String?,
    val dateOfBirth: LocalDate?, val guardianContact: String?, val licenseStage: LicenseStage = LicenseStage.LEARNER
) {
    fun isMinor(): Boolean = dateOfBirth != null && Period.between(dateOfBirth, LocalDate.now()).years < 18
}

data class InstructorProfile(
    val userId: UserId, val firstName: String, val lastName: String, val phone: String?,
    val licenseCertNumber: String?, val yearsExperience: Int = 0,
    val specialties: List<String> = emptyList(), val vehicleAssigned: String?
)
```

**Domain — repository ports**
```kotlin
interface UserAccountRepository {
    fun save(account: UserAccount): UserAccount
    fun findById(id: UserId): UserAccount?
    fun findByEmail(email: String): UserAccount?
    fun existsByEmail(email: String): Boolean
    fun updateStatus(id: UserId, status: AccountStatus)
}
interface StudentProfileRepository {
    fun save(profile: StudentProfile): StudentProfile
    fun findByUserId(id: UserId): StudentProfile?
    fun search(query: String?, page: PageRequest): PageResult<StudentProfile>
}
interface InstructorProfileRepository {
    fun save(profile: InstructorProfile): InstructorProfile
    fun findByUserId(id: UserId): InstructorProfile?
    fun findAll(page: PageRequest): PageResult<InstructorProfile>
}
interface PasswordHasher {
    fun hash(rawPassword: String): String
    fun matches(rawPassword: String, hash: String): Boolean
}
interface TokenIssuer {
    fun issue(userId: UserId, role: UserRole): String
}
```

**Domain — exceptions**
```kotlin
class EmailAlreadyRegisteredException(email: String) : ConflictException("Email already registered: $email")
class MinorMissingGuardianContactException : ValidationException("Guardian contact is required for students under 18")
class UserNotFoundException(id: UserId) : NotFoundException("User not found: ${id.value}")
class InvalidCredentialsException : ValidationException("Invalid email or password")
```

**Application — use cases**
```kotlin
data class RegisterStudentCommand(
    val email: String, val password: String, val firstName: String, val lastName: String,
    val phone: String?, val dateOfBirth: LocalDate?, val guardianContact: String?
)
data class StudentRegisteredPayload(val studentId: UUID, val email: String, val firstName: String, val lastName: String)

class RegisterStudentUseCase(
    private val userAccountRepository: UserAccountRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val passwordHasher: PasswordHasher,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(command: RegisterStudentCommand): StudentProfile {
        if (userAccountRepository.existsByEmail(command.email)) throw EmailAlreadyRegisteredException(command.email)

        val account = userAccountRepository.save(
            UserAccount(UserId(UUID.randomUUID()), command.email, passwordHasher.hash(command.password),
                UserRole.STUDENT, AccountStatus.ACTIVE, Instant.now())
        )
        val profile = StudentProfile(account.id, command.firstName, command.lastName, command.phone,
            command.dateOfBirth, command.guardianContact)
        if (profile.isMinor() && profile.guardianContact.isNullOrBlank()) throw MinorMissingGuardianContactException()

        val saved = studentProfileRepository.save(profile)
        eventPublisher.publish(EventEnvelope(
            eventType = "user.student.registered", source = "identity-service",
            payload = StudentRegisteredPayload(account.id.value, account.email, saved.firstName, saved.lastName)
        ))
        return saved
    }
}

data class RegisterInstructorCommand(
    val email: String, val password: String, val firstName: String, val lastName: String, val phone: String?,
    val licenseCertNumber: String?, val yearsExperience: Int, val specialties: List<String>, val vehicleAssigned: String?
)
data class InstructorRegisteredPayload(val instructorId: UUID, val email: String, val firstName: String, val lastName: String)

class RegisterInstructorUseCase(
    private val userAccountRepository: UserAccountRepository,
    private val instructorProfileRepository: InstructorProfileRepository,
    private val passwordHasher: PasswordHasher,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(command: RegisterInstructorCommand): InstructorProfile {
        if (userAccountRepository.existsByEmail(command.email)) throw EmailAlreadyRegisteredException(command.email)
        val account = userAccountRepository.save(
            UserAccount(UserId(UUID.randomUUID()), command.email, passwordHasher.hash(command.password),
                UserRole.INSTRUCTOR, AccountStatus.ACTIVE, Instant.now())
        )
        val saved = instructorProfileRepository.save(
            InstructorProfile(account.id, command.firstName, command.lastName, command.phone,
                command.licenseCertNumber, command.yearsExperience, command.specialties, command.vehicleAssigned)
        )
        eventPublisher.publish(EventEnvelope(
            eventType = "user.instructor.registered", source = "identity-service",
            payload = InstructorRegisteredPayload(account.id.value, account.email, saved.firstName, saved.lastName)
        ))
        return saved
    }
}

class GetStudentUseCase(private val repo: StudentProfileRepository) {
    fun execute(id: UserId): StudentProfile = repo.findByUserId(id) ?: throw UserNotFoundException(id)
}
class SearchStudentsUseCase(private val repo: StudentProfileRepository) {
    fun execute(query: String?, page: PageRequest): PageResult<StudentProfile> = repo.search(query, page)
}
class UpdateStudentProfileUseCase(private val repo: StudentProfileRepository) {
    fun execute(profile: StudentProfile): StudentProfile = repo.save(profile)
}
class GetInstructorUseCase(private val repo: InstructorProfileRepository) {
    fun execute(id: UserId): InstructorProfile = repo.findByUserId(id) ?: throw UserNotFoundException(id)
}
class ListInstructorsUseCase(private val repo: InstructorProfileRepository) {
    fun execute(page: PageRequest): PageResult<InstructorProfile> = repo.findAll(page)
}
class UpdateInstructorProfileUseCase(private val repo: InstructorProfileRepository) {
    fun execute(profile: InstructorProfile): InstructorProfile = repo.save(profile)
}

data class UserStatusChangedPayload(val userId: UUID, val oldStatus: String, val newStatus: String)

class UpdateAccountStatusUseCase(
    private val userAccountRepository: UserAccountRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(id: UserId, newStatus: AccountStatus) {
        val account = userAccountRepository.findById(id) ?: throw UserNotFoundException(id)
        userAccountRepository.updateStatus(id, newStatus)
        eventPublisher.publish(EventEnvelope(
            eventType = "user.status.changed", source = "identity-service",
            payload = UserStatusChangedPayload(id.value, account.status.name, newStatus.name)
        ))
    }
}

class AuthenticateUserUseCase(
    private val userAccountRepository: UserAccountRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer
) {
    fun execute(email: String, password: String): String {
        val account = userAccountRepository.findByEmail(email) ?: throw InvalidCredentialsException()
        if (!passwordHasher.matches(password, account.passwordHash)) throw InvalidCredentialsException()
        return tokenIssuer.issue(account.id, account.role)
    }
}
```

**Infrastructure — persistence**
```kotlin
@Entity @Table(name = "user_accounts", schema = "identity_schema")
class UserAccountJpaEntity : BaseJpaEntity() {
    lateinit var email: String
    lateinit var passwordHash: String
    @Enumerated(EnumType.STRING) lateinit var role: UserRole
    @Enumerated(EnumType.STRING) var status: AccountStatus = AccountStatus.ACTIVE
    lateinit var createdAt: Instant
}

@Entity @Table(name = "student_profiles", schema = "identity_schema")
class StudentProfileJpaEntity : PanacheEntityBase {
    @Id lateinit var userId: UUID
    lateinit var firstName: String
    lateinit var lastName: String
    var phone: String? = null
    var dateOfBirth: LocalDate? = null
    var guardianContact: String? = null
    @Enumerated(EnumType.STRING) var licenseStage: LicenseStage = LicenseStage.LEARNER
}

@Entity @Table(name = "instructor_profiles", schema = "identity_schema")
class InstructorProfileJpaEntity : PanacheEntityBase {
    @Id lateinit var userId: UUID
    lateinit var firstName: String
    lateinit var lastName: String
    var phone: String? = null
    var licenseCertNumber: String? = null
    var yearsExperience: Int = 0
    @ElementCollection @CollectionTable(name = "instructor_specialties", schema = "identity_schema")
    var specialties: MutableList<String> = mutableListOf()
    var vehicleAssigned: String? = null
}

object UserAccountMapper {
    fun toDomain(e: UserAccountJpaEntity) = UserAccount(UserId(e.id), e.email, e.passwordHash, e.role, e.status, e.createdAt)
    fun toEntity(a: UserAccount) = UserAccountJpaEntity().apply {
        id = a.id.value; email = a.email; passwordHash = a.passwordHash; role = a.role; status = a.status; createdAt = a.createdAt
    }
}
object StudentProfileMapper {
    fun toDomain(e: StudentProfileJpaEntity) = StudentProfile(UserId(e.userId), e.firstName, e.lastName, e.phone,
        e.dateOfBirth, e.guardianContact, e.licenseStage)
    fun toEntity(p: StudentProfile) = StudentProfileJpaEntity().apply {
        userId = p.userId.value; firstName = p.firstName; lastName = p.lastName; phone = p.phone
        dateOfBirth = p.dateOfBirth; guardianContact = p.guardianContact; licenseStage = p.licenseStage
    }
}
object InstructorProfileMapper {
    fun toDomain(e: InstructorProfileJpaEntity) = InstructorProfile(UserId(e.userId), e.firstName, e.lastName, e.phone,
        e.licenseCertNumber, e.yearsExperience, e.specialties.toList(), e.vehicleAssigned)
    fun toEntity(p: InstructorProfile) = InstructorProfileJpaEntity().apply {
        userId = p.userId.value; firstName = p.firstName; lastName = p.lastName; phone = p.phone
        licenseCertNumber = p.licenseCertNumber; yearsExperience = p.yearsExperience
        specialties = p.specialties.toMutableList(); vehicleAssigned = p.vehicleAssigned
    }
}

@ApplicationScoped
class PanacheUserAccountRepository : UserAccountRepository, PanacheRepositoryBase<UserAccountJpaEntity, UUID> {
    override fun save(account: UserAccount): UserAccount {
        val e = UserAccountMapper.toEntity(account); persist(e); return UserAccountMapper.toDomain(e)
    }
    override fun findById(id: UserId) = findByIdOptional(id.value).map(UserAccountMapper::toDomain).orElse(null)
    override fun findByEmail(email: String) = find("email", email).firstResultOptional<UserAccountJpaEntity>()
        .map(UserAccountMapper::toDomain).orElse(null)
    override fun existsByEmail(email: String) = count("email", email) > 0
    override fun updateStatus(id: UserId, status: AccountStatus) {
        update("status = ?1 where id = ?2", status, id.value)
    }
}

@ApplicationScoped
class PanacheStudentProfileRepository : StudentProfileRepository, PanacheRepositoryBase<StudentProfileJpaEntity, UUID> {
    override fun save(profile: StudentProfile): StudentProfile {
        val e = StudentProfileMapper.toEntity(profile); persist(e); return StudentProfileMapper.toDomain(e)
    }
    override fun findByUserId(id: UserId) = findByIdOptional(id.value).map(StudentProfileMapper::toDomain).orElse(null)
    override fun search(query: String?, page: PageRequest): PageResult<StudentProfile> {
        val q = if (query == null) findAll() else find(
            "lower(firstName) like ?1 or lower(lastName) like ?1", "%${query.lowercase()}%"
        )
        val items = q.page(page.page, page.size).list().map(StudentProfileMapper::toDomain)
        return PageResult(items, page.page, page.size, q.count())
    }
}

@ApplicationScoped
class PanacheInstructorProfileRepository : InstructorProfileRepository, PanacheRepositoryBase<InstructorProfileJpaEntity, UUID> {
    override fun save(profile: InstructorProfile): InstructorProfile {
        val e = InstructorProfileMapper.toEntity(profile); persist(e); return InstructorProfileMapper.toDomain(e)
    }
    override fun findByUserId(id: UserId) = findByIdOptional(id.value).map(InstructorProfileMapper::toDomain).orElse(null)
    override fun findAll(page: PageRequest): PageResult<InstructorProfile> {
        val q = findAll()
        val items = q.page(page.page, page.size).list().map(InstructorProfileMapper::toDomain)
        return PageResult(items, page.page, page.size, q.count())
    }
}
```

**Infrastructure — auth adapters, messaging**
```kotlin
@ApplicationScoped
class BCryptPasswordHasher : PasswordHasher {
    override fun hash(rawPassword: String): String = BcryptUtil.bcryptHash(rawPassword)
    override fun matches(rawPassword: String, hash: String): Boolean = BcryptUtil.matches(rawPassword, hash)
}

@ApplicationScoped
class SmallRyeTokenIssuer : TokenIssuer {
    override fun issue(userId: UserId, role: UserRole): String =
        Jwt.issuer("identity-service").subject(userId.value.toString())
            .groups(setOf(role.name)).expiresIn(Duration.ofHours(8)).sign()
}

@ApplicationScoped
class RabbitMqEventPublisher(@Channel("ds-events-out") emitter: Emitter<EventEnvelope<*>>) :
    AbstractRabbitMqEventPublisher(emitter)
```

**Presentation**
```kotlin
data class RegisterStudentRequest(val email: String, val password: String, val firstName: String, val lastName: String,
    val phone: String?, val dateOfBirth: LocalDate?, val guardianContact: String?)
data class RegisterInstructorRequest(val email: String, val password: String, val firstName: String, val lastName: String,
    val phone: String?, val licenseCertNumber: String?, val yearsExperience: Int, val specialties: List<String>, val vehicleAssigned: String?)
data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String)
data class StudentResponse(val userId: UUID, val email: String, val firstName: String, val lastName: String, val licenseStage: String)
data class InstructorResponse(val userId: UUID, val firstName: String, val lastName: String, val specialties: List<String>)
data class UpdateStatusRequest(val status: AccountStatus)

@Path("/api/v1/students")
class StudentResource(
    private val registerStudentUseCase: RegisterStudentUseCase,
    private val getStudentUseCase: GetStudentUseCase,
    private val searchStudentsUseCase: SearchStudentsUseCase,
    private val updateStudentProfileUseCase: UpdateStudentProfileUseCase
) {
    @POST @RunOnVirtualThread
    fun register(request: RegisterStudentRequest): Response {
        val p = registerStudentUseCase.execute(RegisterStudentCommand(request.email, request.password, request.firstName,
            request.lastName, request.phone, request.dateOfBirth, request.guardianContact))
        return Response.status(201).entity(toResponse(p)).build()
    }
    @GET @Path("/{id}") @RunOnVirtualThread
    fun get(@PathParam("id") id: UUID): StudentResponse = toResponse(getStudentUseCase.execute(UserId(id)))
    @GET @RunOnVirtualThread
    fun search(@QueryParam("q") q: String?, @QueryParam("page") page: Int?, @QueryParam("size") size: Int?) =
        searchStudentsUseCase.execute(q, PageRequest(page ?: 0, size ?: 20))
    @PUT @Path("/{id}") @RunOnVirtualThread
    fun update(@PathParam("id") id: UUID, request: RegisterStudentRequest): StudentResponse {
        val current = getStudentUseCase.execute(UserId(id))
        return toResponse(updateStudentProfileUseCase.execute(current.copy(
            firstName = request.firstName, lastName = request.lastName, phone = request.phone,
            guardianContact = request.guardianContact)))
    }
    private fun toResponse(p: StudentProfile) = StudentResponse(p.userId.value, "", p.firstName, p.lastName, p.licenseStage.name)
}

@Path("/api/v1/instructors")
class InstructorResource(
    private val registerInstructorUseCase: RegisterInstructorUseCase,
    private val getInstructorUseCase: GetInstructorUseCase,
    private val listInstructorsUseCase: ListInstructorsUseCase,
    private val updateInstructorProfileUseCase: UpdateInstructorProfileUseCase
) {
    @POST @RunOnVirtualThread
    fun register(request: RegisterInstructorRequest): Response {
        val p = registerInstructorUseCase.execute(RegisterInstructorCommand(request.email, request.password,
            request.firstName, request.lastName, request.phone, request.licenseCertNumber, request.yearsExperience,
            request.specialties, request.vehicleAssigned))
        return Response.status(201).entity(toResponse(p)).build()
    }
    @GET @Path("/{id}") @RunOnVirtualThread
    fun get(@PathParam("id") id: UUID) = toResponse(getInstructorUseCase.execute(UserId(id)))
    @GET @RunOnVirtualThread
    fun list(@QueryParam("page") page: Int?, @QueryParam("size") size: Int?) =
        listInstructorsUseCase.execute(PageRequest(page ?: 0, size ?: 20))
    @PUT @Path("/{id}") @RunOnVirtualThread
    fun update(@PathParam("id") id: UUID, request: RegisterInstructorRequest): InstructorResponse {
        val current = getInstructorUseCase.execute(UserId(id))
        return toResponse(updateInstructorProfileUseCase.execute(current.copy(
            firstName = request.firstName, lastName = request.lastName, phone = request.phone,
            specialties = request.specialties, vehicleAssigned = request.vehicleAssigned)))
    }
    private fun toResponse(p: InstructorProfile) = InstructorResponse(p.userId.value, p.firstName, p.lastName, p.specialties)
}

@Path("/api/v1/auth")
class AuthResource(private val authenticateUserUseCase: AuthenticateUserUseCase) {
    @POST @Path("/login") @RunOnVirtualThread
    fun login(request: LoginRequest): LoginResponse = LoginResponse(authenticateUserUseCase.execute(request.email, request.password))
}

@Path("/api/v1/users")
class UserResource(private val updateAccountStatusUseCase: UpdateAccountStatusUseCase) {
    @PATCH @Path("/{id}/status") @RunOnVirtualThread
    fun updateStatus(@PathParam("id") id: UUID, request: UpdateStatusRequest): Response {
        updateAccountStatusUseCase.execute(UserId(id), request.status)
        return Response.noContent().build()
    }
}

@Provider
class IdentityExceptionMapper : BaseExceptionMapper()
```

**Tables:** `user_accounts`, `student_profiles`, `instructor_profiles`, `instructor_specialties` in `identity_schema` (DDL in §7).

**REST API, publishes/consumes:** unchanged from earlier revisions — see the routing table in §6.

---

### 4.2 course-service

**Domain — model**
```kotlin
@JvmInline value class CourseId(val value: UUID)
@JvmInline value class BatchId(val value: UUID)
@JvmInline value class SessionId(val value: UUID)
@JvmInline value class EnrollmentId(val value: UUID)
enum class CourseType { THEORY, PRACTICAL, COMBO }
enum class BatchStatus { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }
enum class SessionStatus { PLANNED, HELD, CANCELLED }
enum class EnrollmentStatus { ACTIVE, WITHDRAWN, COMPLETED }

data class Course(val id: CourseId, val title: String, val description: String, val courseType: CourseType,
    val durationHours: Int, val price: BigDecimal, val active: Boolean = true)

data class ClassBatch(val id: BatchId, val courseId: CourseId, val instructorId: UUID, val startDate: LocalDate,
    val endDate: LocalDate, val capacity: Int = 20, val status: BatchStatus = BatchStatus.SCHEDULED)

data class ClassSession(val id: SessionId, val batchId: BatchId, val sessionDate: LocalDate, val startTime: LocalTime,
    val endTime: LocalTime, val location: String?, val status: SessionStatus = SessionStatus.PLANNED)

data class Enrollment(val id: EnrollmentId, val studentId: UUID, val batchId: BatchId, val enrolledAt: Instant,
    val status: EnrollmentStatus = EnrollmentStatus.ACTIVE) {
    companion object {
        fun create(studentId: UUID, batchId: BatchId) = Enrollment(EnrollmentId(UUID.randomUUID()), studentId, batchId, Instant.now())
    }
}
```

**Domain — ports**
```kotlin
interface CourseRepository {
    fun save(course: Course): Course
    fun findById(id: CourseId): Course?
    fun list(activeOnly: Boolean, type: CourseType?, page: PageRequest): PageResult<Course>
}
interface ClassBatchRepository {
    fun save(batch: ClassBatch): ClassBatch
    fun findById(id: BatchId): ClassBatch?
    fun findByIdForUpdate(id: BatchId): ClassBatch?     // infra implements with SELECT ... FOR UPDATE
    fun list(courseId: CourseId?, instructorId: UUID?, status: BatchStatus?, page: PageRequest): PageResult<ClassBatch>
}
interface ClassSessionRepository {
    fun save(session: ClassSession): ClassSession
    fun findById(id: SessionId): ClassSession?
    fun listByBatch(batchId: BatchId): List<ClassSession>
}
interface EnrollmentRepository {
    fun save(enrollment: Enrollment): Enrollment
    fun countActiveForBatch(batchId: BatchId): Long
    fun existsActive(studentId: UUID, batchId: BatchId): Boolean
    fun findById(id: EnrollmentId): Enrollment?
    fun listByStudent(studentId: UUID): List<Enrollment>
    fun updateStatus(id: EnrollmentId, status: EnrollmentStatus)
}
```

**Domain — exceptions**
```kotlin
class CourseNotFoundException(id: CourseId) : NotFoundException("Course not found: ${id.value}")
class BatchNotFoundException(id: BatchId) : NotFoundException("Batch not found: ${id.value}")
class BatchAtCapacityException(id: BatchId) : ConflictException("Batch at capacity: ${id.value}")
class AlreadyEnrolledException(studentId: UUID, batchId: BatchId) :
    ConflictException("Student $studentId already enrolled in batch ${batchId.value}")
class EnrollmentNotFoundException(id: EnrollmentId) : NotFoundException("Enrollment not found: ${id.value}")
```

**Application — use cases**
```kotlin
data class CreateCourseCommand(val title: String, val description: String, val courseType: CourseType,
    val durationHours: Int, val price: BigDecimal)

class CreateCourseUseCase(private val repo: CourseRepository) {
    fun execute(cmd: CreateCourseCommand): Course =
        repo.save(Course(CourseId(UUID.randomUUID()), cmd.title, cmd.description, cmd.courseType, cmd.durationHours, cmd.price))
}
class GetCourseUseCase(private val repo: CourseRepository) {
    fun execute(id: CourseId): Course = repo.findById(id) ?: throw CourseNotFoundException(id)
}
class ListCoursesUseCase(private val repo: CourseRepository) {
    fun execute(activeOnly: Boolean, type: CourseType?, page: PageRequest) = repo.list(activeOnly, type, page)
}
class UpdateCourseUseCase(private val repo: CourseRepository) {
    fun execute(course: Course): Course = repo.save(course)
}

data class BatchCreatedPayload(val batchId: UUID, val courseId: UUID, val instructorId: UUID, val startDate: LocalDate, val endDate: LocalDate)

class CreateClassBatchUseCase(
    private val courseRepository: CourseRepository,
    private val batchRepository: ClassBatchRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(courseId: CourseId, instructorId: UUID, startDate: LocalDate, endDate: LocalDate, capacity: Int): ClassBatch {
        courseRepository.findById(courseId) ?: throw CourseNotFoundException(courseId)
        val batch = batchRepository.save(ClassBatch(BatchId(UUID.randomUUID()), courseId, instructorId, startDate, endDate, capacity))
        eventPublisher.publish(EventEnvelope(eventType = "class.batch.created", source = "course-service",
            payload = BatchCreatedPayload(batch.id.value, courseId.value, instructorId, startDate, endDate)))
        return batch
    }
}
class ListBatchesUseCase(private val repo: ClassBatchRepository) {
    fun execute(courseId: CourseId?, instructorId: UUID?, status: BatchStatus?, page: PageRequest) =
        repo.list(courseId, instructorId, status, page)
}

data class SessionScheduledPayload(val sessionId: UUID, val batchId: UUID, val sessionDate: LocalDate, val startTime: LocalTime, val endTime: LocalTime)

class ScheduleSessionUseCase(
    private val batchRepository: ClassBatchRepository,
    private val sessionRepository: ClassSessionRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(batchId: BatchId, date: LocalDate, start: LocalTime, end: LocalTime, location: String?): ClassSession {
        batchRepository.findById(batchId) ?: throw BatchNotFoundException(batchId)
        val session = sessionRepository.save(ClassSession(SessionId(UUID.randomUUID()), batchId, date, start, end, location))
        eventPublisher.publish(EventEnvelope(eventType = "class.session.scheduled", source = "course-service",
            payload = SessionScheduledPayload(session.id.value, batchId.value, date, start, end)))
        return session
    }
}

data class SessionRescheduledPayload(val sessionId: UUID, val oldDate: LocalDate, val newDate: LocalDate)

class RescheduleSessionUseCase(
    private val sessionRepository: ClassSessionRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(sessionId: SessionId, newDate: LocalDate, newStart: LocalTime, newEnd: LocalTime): ClassSession {
        val existing = sessionRepository.findById(sessionId) ?: throw SessionNotFoundException(sessionId)
        val updated = sessionRepository.save(existing.copy(sessionDate = newDate, startTime = newStart, endTime = newEnd))
        eventPublisher.publish(EventEnvelope(eventType = "class.session.rescheduled", source = "course-service",
            payload = SessionRescheduledPayload(sessionId.value, existing.sessionDate, newDate)))
        return updated
    }
}
class SessionNotFoundException(id: SessionId) : NotFoundException("Session not found: ${id.value}")

class CancelSessionUseCase(
    private val sessionRepository: ClassSessionRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(sessionId: SessionId) {
        val existing = sessionRepository.findById(sessionId) ?: throw SessionNotFoundException(sessionId)
        sessionRepository.save(existing.copy(status = SessionStatus.CANCELLED))
        eventPublisher.publish(EventEnvelope(eventType = "class.session.cancelled", source = "course-service",
            payload = mapOf("sessionId" to sessionId.value, "batchId" to existing.batchId.value)))
    }
}

data class EnrollmentCreatedPayload(val enrollmentId: UUID, val studentId: UUID, val batchId: UUID, val courseId: UUID, val coursePrice: BigDecimal)

class EnrollStudentUseCase(
    private val courseRepository: CourseRepository,
    private val batchRepository: ClassBatchRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(studentId: UUID, batchId: BatchId): Enrollment {
        val batch = batchRepository.findByIdForUpdate(batchId) ?: throw BatchNotFoundException(batchId)
        val course = courseRepository.findById(batch.courseId) ?: throw CourseNotFoundException(batch.courseId)

        if (enrollmentRepository.countActiveForBatch(batch.id) >= batch.capacity) throw BatchAtCapacityException(batch.id)
        if (enrollmentRepository.existsActive(studentId, batch.id)) throw AlreadyEnrolledException(studentId, batch.id)

        val enrollment = enrollmentRepository.save(Enrollment.create(studentId, batch.id))
        eventPublisher.publish(EventEnvelope(eventType = "course.enrollment.created", source = "course-service",
            payload = EnrollmentCreatedPayload(enrollment.id.value, studentId, batch.id.value, course.id.value, course.price)))
        return enrollment
    }
}

class WithdrawEnrollmentUseCase(
    private val enrollmentRepository: EnrollmentRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(enrollmentId: EnrollmentId) {
        val enrollment = enrollmentRepository.findById(enrollmentId) ?: throw EnrollmentNotFoundException(enrollmentId)
        enrollmentRepository.updateStatus(enrollmentId, EnrollmentStatus.WITHDRAWN)
        eventPublisher.publish(EventEnvelope(eventType = "course.enrollment.withdrawn", source = "course-service",
            payload = mapOf("enrollmentId" to enrollmentId.value, "studentId" to enrollment.studentId, "batchId" to enrollment.batchId.value)))
    }
}
class ListEnrollmentsForStudentUseCase(private val repo: EnrollmentRepository) {
    fun execute(studentId: UUID): List<Enrollment> = repo.listByStudent(studentId)
}
```

**Infrastructure — persistence**
```kotlin
@Entity @Table(name = "courses", schema = "course_schema")
class CourseJpaEntity : BaseJpaEntity() {
    lateinit var title: String
    lateinit var description: String
    @Enumerated(EnumType.STRING) lateinit var courseType: CourseType
    var durationHours: Int = 0
    var price: BigDecimal = BigDecimal.ZERO
    var active: Boolean = true
}
@Entity @Table(name = "class_batches", schema = "course_schema")
class ClassBatchJpaEntity : BaseJpaEntity() {
    lateinit var courseId: UUID
    lateinit var instructorId: UUID
    lateinit var startDate: LocalDate
    lateinit var endDate: LocalDate
    var capacity: Int = 20
    @Enumerated(EnumType.STRING) var status: BatchStatus = BatchStatus.SCHEDULED
}
@Entity @Table(name = "class_sessions", schema = "course_schema")
class ClassSessionJpaEntity : BaseJpaEntity() {
    lateinit var batchId: UUID
    lateinit var sessionDate: LocalDate
    lateinit var startTime: LocalTime
    lateinit var endTime: LocalTime
    var location: String? = null
    @Enumerated(EnumType.STRING) var status: SessionStatus = SessionStatus.PLANNED
}
@Entity @Table(name = "enrollments", schema = "course_schema")
class EnrollmentJpaEntity : BaseJpaEntity() {
    lateinit var studentId: UUID
    lateinit var batchId: UUID
    lateinit var enrolledAt: Instant
    @Enumerated(EnumType.STRING) var status: EnrollmentStatus = EnrollmentStatus.ACTIVE
}

object CourseMapper {
    fun toDomain(e: CourseJpaEntity) = Course(CourseId(e.id), e.title, e.description, e.courseType, e.durationHours, e.price, e.active)
    fun toEntity(c: Course) = CourseJpaEntity().apply {
        id = c.id.value; title = c.title; description = c.description; courseType = c.courseType
        durationHours = c.durationHours; price = c.price; active = c.active
    }
}
object ClassBatchMapper {
    fun toDomain(e: ClassBatchJpaEntity) = ClassBatch(BatchId(e.id), CourseId(e.courseId), e.instructorId, e.startDate, e.endDate, e.capacity, e.status)
    fun toEntity(b: ClassBatch) = ClassBatchJpaEntity().apply {
        id = b.id.value; courseId = b.courseId.value; instructorId = b.instructorId
        startDate = b.startDate; endDate = b.endDate; capacity = b.capacity; status = b.status
    }
}
object ClassSessionMapper {
    fun toDomain(e: ClassSessionJpaEntity) = ClassSession(SessionId(e.id), BatchId(e.batchId), e.sessionDate, e.startTime, e.endTime, e.location, e.status)
    fun toEntity(s: ClassSession) = ClassSessionJpaEntity().apply {
        id = s.id.value; batchId = s.batchId.value; sessionDate = s.sessionDate
        startTime = s.startTime; endTime = s.endTime; location = s.location; status = s.status
    }
}
object EnrollmentMapper {
    fun toDomain(e: EnrollmentJpaEntity) = Enrollment(EnrollmentId(e.id), e.studentId, BatchId(e.batchId), e.enrolledAt, e.status)
    fun toEntity(en: Enrollment) = EnrollmentJpaEntity().apply {
        id = en.id.value; studentId = en.studentId; batchId = en.batchId.value; enrolledAt = en.enrolledAt; status = en.status
    }
}

@ApplicationScoped
class PanacheCourseRepository : CourseRepository, PanacheRepositoryBase<CourseJpaEntity, UUID> {
    override fun save(course: Course): Course { val e = CourseMapper.toEntity(course); persist(e); return CourseMapper.toDomain(e) }
    override fun findById(id: CourseId) = findByIdOptional(id.value).map(CourseMapper::toDomain).orElse(null)
    override fun list(activeOnly: Boolean, type: CourseType?, page: PageRequest): PageResult<Course> {
        val q = when {
            activeOnly && type != null -> find("active = true and courseType = ?1", type)
            activeOnly -> find("active = true")
            type != null -> find("courseType", type)
            else -> findAll()
        }
        return PageResult(q.page(page.page, page.size).list().map(CourseMapper::toDomain), page.page, page.size, q.count())
    }
}

@ApplicationScoped
class PanacheClassBatchRepository : ClassBatchRepository, PanacheRepositoryBase<ClassBatchJpaEntity, UUID> {
    override fun save(batch: ClassBatch): ClassBatch { val e = ClassBatchMapper.toEntity(batch); persist(e); return ClassBatchMapper.toDomain(e) }
    override fun findById(id: BatchId) = findByIdOptional(id.value).map(ClassBatchMapper::toDomain).orElse(null)
    override fun findByIdForUpdate(id: BatchId): ClassBatch? =
        find("id", id.value).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional<ClassBatchJpaEntity>()
            .map(ClassBatchMapper::toDomain).orElse(null)
    override fun list(courseId: CourseId?, instructorId: UUID?, status: BatchStatus?, page: PageRequest): PageResult<ClassBatch> {
        val q = findAll() // filters composed similarly to CourseRepository.list, omitted for brevity
        return PageResult(q.page(page.page, page.size).list().map(ClassBatchMapper::toDomain), page.page, page.size, q.count())
    }
}

@ApplicationScoped
class PanacheClassSessionRepository : ClassSessionRepository, PanacheRepositoryBase<ClassSessionJpaEntity, UUID> {
    override fun save(session: ClassSession): ClassSession { val e = ClassSessionMapper.toEntity(session); persist(e); return ClassSessionMapper.toDomain(e) }
    override fun findById(id: SessionId) = findByIdOptional(id.value).map(ClassSessionMapper::toDomain).orElse(null)
    override fun listByBatch(batchId: BatchId) = find("batchId", batchId.value).list().map(ClassSessionMapper::toDomain)
}

@ApplicationScoped
class PanacheEnrollmentRepository : EnrollmentRepository, PanacheRepositoryBase<EnrollmentJpaEntity, UUID> {
    override fun save(enrollment: Enrollment): Enrollment { val e = EnrollmentMapper.toEntity(enrollment); persist(e); return EnrollmentMapper.toDomain(e) }
    override fun countActiveForBatch(batchId: BatchId) = count("batchId = ?1 and status = ?2", batchId.value, EnrollmentStatus.ACTIVE)
    override fun existsActive(studentId: UUID, batchId: BatchId) =
        count("studentId = ?1 and batchId = ?2 and status = ?3", studentId, batchId.value, EnrollmentStatus.ACTIVE) > 0
    override fun findById(id: EnrollmentId) = findByIdOptional(id.value).map(EnrollmentMapper::toDomain).orElse(null)
    override fun listByStudent(studentId: UUID) = find("studentId", studentId).list().map(EnrollmentMapper::toDomain)
    override fun updateStatus(id: EnrollmentId, status: EnrollmentStatus) { update("status = ?1 where id = ?2", status, id.value) }
}

@ApplicationScoped
class RabbitMqEventPublisher(@Channel("ds-events-out") emitter: Emitter<EventEnvelope<*>>) :
    AbstractRabbitMqEventPublisher(emitter)
```

**Presentation:** `CourseResource`, `BatchResource` (with nested `/sessions` sub-paths), `EnrollmentResource`, matching request/response DTOs for each command above, and `CourseExceptionMapper : BaseExceptionMapper()`. REST paths/roles unchanged from earlier revisions.

**Tables:** `courses`, `class_batches`, `class_sessions`, `enrollments` in `course_schema` (DDL §7).

---

### 4.3 appointment-service

**Domain — model**
```kotlin
@JvmInline value class AppointmentId(val value: UUID)
@JvmInline value class AvailabilitySlotId(val value: UUID)
enum class AppointmentStatus { CONFIRMED, RESCHEDULED, CANCELLED, COMPLETED, NO_SHOW }

data class AvailabilitySlot(val id: AvailabilitySlotId, val instructorId: UUID, val dayOfWeek: DayOfWeek,
    val startTime: LocalTime, val endTime: LocalTime)

data class Appointment(val id: AppointmentId, val studentId: UUID, val instructorId: UUID, val vehicleId: String?,
    val startAt: Instant, val endAt: Instant, val pickupLocation: String?,
    val status: AppointmentStatus = AppointmentStatus.CONFIRMED, val notes: String? = null,
    val reminderSentAt: Instant? = null)
```

**Domain — ports**
```kotlin
interface AvailabilityRepository {
    fun save(slot: AvailabilitySlot): AvailabilitySlot
    fun findForInstructor(instructorId: UUID): List<AvailabilitySlot>
}
interface AppointmentRepository {
    fun save(appointment: Appointment): Appointment
    fun findById(id: AppointmentId): Appointment?
    fun overlapsExisting(instructorId: UUID, startAt: Instant, endAt: Instant): Boolean
    fun list(studentId: UUID?, instructorId: UUID?, from: Instant?, to: Instant?): List<Appointment>
    fun findStartingWithinWithNoReminderSent(withinHours: Long): List<Appointment>
    fun markReminderSent(id: AppointmentId)
    fun updateStatus(id: AppointmentId, status: AppointmentStatus)
}
```

**Domain — exceptions**
```kotlin
class AppointmentNotFoundException(id: AppointmentId) : NotFoundException("Appointment not found: ${id.value}")
class SlotConflictException(instructorId: UUID) : ConflictException("Instructor $instructorId already booked for that time")
```

**Application — use cases**
```kotlin
class DefineAvailabilityUseCase(private val repo: AvailabilityRepository) {
    fun execute(instructorId: UUID, dayOfWeek: DayOfWeek, start: LocalTime, end: LocalTime): AvailabilitySlot =
        repo.save(AvailabilitySlot(AvailabilitySlotId(UUID.randomUUID()), instructorId, dayOfWeek, start, end))
}
class GetAvailableSlotsUseCase(private val repo: AvailabilityRepository) {
    fun execute(instructorId: UUID): List<AvailabilitySlot> = repo.findForInstructor(instructorId)
}

data class AppointmentCreatedPayload(val appointmentId: UUID, val studentId: UUID, val instructorId: UUID, val startAt: Instant)

class BookAppointmentUseCase(
    private val appointmentRepository: AppointmentRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(studentId: UUID, instructorId: UUID, startAt: Instant, endAt: Instant, pickupLocation: String?): Appointment {
        if (appointmentRepository.overlapsExisting(instructorId, startAt, endAt)) throw SlotConflictException(instructorId)
        val appointment = appointmentRepository.save(Appointment(AppointmentId(UUID.randomUUID()), studentId, instructorId,
            null, startAt, endAt, pickupLocation))
        eventPublisher.publish(EventEnvelope(eventType = "appointment.created", source = "appointment-service",
            payload = AppointmentCreatedPayload(appointment.id.value, studentId, instructorId, startAt)))
        return appointment
    }
}

class RescheduleAppointmentUseCase(
    private val appointmentRepository: AppointmentRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(id: AppointmentId, newStart: Instant, newEnd: Instant): Appointment {
        val existing = appointmentRepository.findById(id) ?: throw AppointmentNotFoundException(id)
        if (appointmentRepository.overlapsExisting(existing.instructorId, newStart, newEnd)) throw SlotConflictException(existing.instructorId)
        val updated = appointmentRepository.save(existing.copy(startAt = newStart, endAt = newEnd, status = AppointmentStatus.RESCHEDULED))
        eventPublisher.publish(EventEnvelope(eventType = "appointment.rescheduled", source = "appointment-service",
            payload = mapOf("appointmentId" to id.value, "oldStart" to existing.startAt, "newStart" to newStart)))
        return updated
    }
}

class CancelAppointmentUseCase(
    private val appointmentRepository: AppointmentRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(id: AppointmentId) {
        val existing = appointmentRepository.findById(id) ?: throw AppointmentNotFoundException(id)
        appointmentRepository.updateStatus(id, AppointmentStatus.CANCELLED)
        eventPublisher.publish(EventEnvelope(eventType = "appointment.cancelled", source = "appointment-service",
            payload = mapOf("appointmentId" to id.value, "studentId" to existing.studentId)))
    }
}
class CompleteAppointmentUseCase(private val repo: AppointmentRepository) {
    fun execute(id: AppointmentId) { repo.updateStatus(id, AppointmentStatus.COMPLETED) }
}
class ListAppointmentsUseCase(private val repo: AppointmentRepository) {
    fun execute(studentId: UUID?, instructorId: UUID?, from: Instant?, to: Instant?) = repo.list(studentId, instructorId, from, to)
}

data class AppointmentReminderDuePayload(val appointmentId: UUID, val studentId: UUID, val instructorId: UUID, val startAt: Instant, val pickupLocation: String?)

class FindDueReminderAppointmentsUseCase(
    private val appointmentRepository: AppointmentRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute() {
        appointmentRepository.findStartingWithinWithNoReminderSent(withinHours = 24).forEach { appointment ->
            eventPublisher.publish(EventEnvelope(eventType = "appointment.reminder.due", source = "appointment-service",
                payload = AppointmentReminderDuePayload(appointment.id.value, appointment.studentId, appointment.instructorId,
                    appointment.startAt, appointment.pickupLocation)))
            appointmentRepository.markReminderSent(appointment.id)
        }
    }
}
```

**Infrastructure**
```kotlin
@Entity @Table(name = "instructor_availability", schema = "appointment_schema")
class AvailabilitySlotJpaEntity : BaseJpaEntity() {
    lateinit var instructorId: UUID
    @Enumerated(EnumType.STRING) lateinit var dayOfWeek: DayOfWeek
    lateinit var startTime: LocalTime
    lateinit var endTime: LocalTime
}
@Entity @Table(name = "appointments", schema = "appointment_schema")
class AppointmentJpaEntity : BaseJpaEntity() {
    lateinit var studentId: UUID
    lateinit var instructorId: UUID
    var vehicleId: String? = null
    lateinit var startAt: Instant
    lateinit var endAt: Instant
    var pickupLocation: String? = null
    @Enumerated(EnumType.STRING) var status: AppointmentStatus = AppointmentStatus.CONFIRMED
    var notes: String? = null
    var reminderSentAt: Instant? = null
}

object AvailabilitySlotMapper {
    fun toDomain(e: AvailabilitySlotJpaEntity) = AvailabilitySlot(AvailabilitySlotId(e.id), e.instructorId, e.dayOfWeek, e.startTime, e.endTime)
    fun toEntity(s: AvailabilitySlot) = AvailabilitySlotJpaEntity().apply {
        id = s.id.value; instructorId = s.instructorId; dayOfWeek = s.dayOfWeek; startTime = s.startTime; endTime = s.endTime
    }
}
object AppointmentMapper {
    fun toDomain(e: AppointmentJpaEntity) = Appointment(AppointmentId(e.id), e.studentId, e.instructorId, e.vehicleId,
        e.startAt, e.endAt, e.pickupLocation, e.status, e.notes, e.reminderSentAt)
    fun toEntity(a: Appointment) = AppointmentJpaEntity().apply {
        id = a.id.value; studentId = a.studentId; instructorId = a.instructorId; vehicleId = a.vehicleId
        startAt = a.startAt; endAt = a.endAt; pickupLocation = a.pickupLocation; status = a.status
        notes = a.notes; reminderSentAt = a.reminderSentAt
    }
}

@ApplicationScoped
class PanacheAvailabilityRepository : AvailabilityRepository, PanacheRepositoryBase<AvailabilitySlotJpaEntity, UUID> {
    override fun save(slot: AvailabilitySlot): AvailabilitySlot { val e = AvailabilitySlotMapper.toEntity(slot); persist(e); return AvailabilitySlotMapper.toDomain(e) }
    override fun findForInstructor(instructorId: UUID) = find("instructorId", instructorId).list().map(AvailabilitySlotMapper::toDomain)
}

@ApplicationScoped
class PanacheAppointmentRepository : AppointmentRepository, PanacheRepositoryBase<AppointmentJpaEntity, UUID> {
    override fun save(appointment: Appointment): Appointment { val e = AppointmentMapper.toEntity(appointment); persist(e); return AppointmentMapper.toDomain(e) }
    override fun findById(id: AppointmentId) = findByIdOptional(id.value).map(AppointmentMapper::toDomain).orElse(null)
    override fun overlapsExisting(instructorId: UUID, startAt: Instant, endAt: Instant) =
        count("instructorId = ?1 and status != ?2 and startAt < ?3 and endAt > ?4",
            instructorId, AppointmentStatus.CANCELLED, endAt, startAt) > 0
    override fun list(studentId: UUID?, instructorId: UUID?, from: Instant?, to: Instant?) =
        findAll().list().map(AppointmentMapper::toDomain) // filters composed as needed, omitted for brevity
    override fun findStartingWithinWithNoReminderSent(withinHours: Long): List<Appointment> {
        val cutoff = Instant.now().plus(withinHours, ChronoUnit.HOURS)
        return find("startAt <= ?1 and reminderSentAt is null and status = ?2", cutoff, AppointmentStatus.CONFIRMED)
            .list().map(AppointmentMapper::toDomain)
    }
    override fun markReminderSent(id: AppointmentId) { update("reminderSentAt = ?1 where id = ?2", Instant.now(), id.value) }
    override fun updateStatus(id: AppointmentId, status: AppointmentStatus) { update("status = ?1 where id = ?2", status, id.value) }
}

@ApplicationScoped
class RabbitMqEventPublisher(@Channel("ds-events-out") emitter: Emitter<EventEnvelope<*>>) :
    AbstractRabbitMqEventPublisher(emitter)

@ApplicationScoped
class AppointmentReminderJob(private val findDueReminderAppointmentsUseCase: FindDueReminderAppointmentsUseCase) {
    @Scheduled(every = "15m") @RunOnVirtualThread
    fun run() = findDueReminderAppointmentsUseCase.execute()
}
```

**Presentation:** `AppointmentResource`, `AvailabilityResource` + request/response DTOs, `AppointmentExceptionMapper : BaseExceptionMapper()`.

**Tables:** `instructor_availability`, `appointments` in `appointment_schema` (DDL §7).

---

### 4.4 test-service

**Domain**
```kotlin
@JvmInline value class TestScheduleId(val value: UUID)
enum class TestType { MOCK_THEORY, MOCK_PRACTICAL, OFFICIAL_THEORY, OFFICIAL_ROAD }
enum class TestScheduleStatus { SCHEDULED, RESCHEDULED, CANCELLED, COMPLETED }
enum class TestResult { PASS, FAIL, PENDING }

data class TestSchedule(val id: TestScheduleId, val studentId: UUID, val testType: TestType, val scheduledAt: Instant,
    val location: String?, val examinerOrInstructorId: UUID?, val status: TestScheduleStatus = TestScheduleStatus.SCHEDULED,
    val result: TestResult? = null, val resultNotes: String? = null, val reminderSentAt: Instant? = null)

interface TestScheduleRepository {
    fun save(test: TestSchedule): TestSchedule
    fun findById(id: TestScheduleId): TestSchedule?
    fun listByStudent(studentId: UUID): List<TestSchedule>
    fun findStartingWithinWithNoReminderSent(withinHours: Long): List<TestSchedule>
    fun markReminderSent(id: TestScheduleId)
    fun updateStatus(id: TestScheduleId, status: TestScheduleStatus)
    fun recordResult(id: TestScheduleId, result: TestResult, notes: String?)
}

class TestScheduleNotFoundException(id: TestScheduleId) : NotFoundException("Test schedule not found: ${id.value}")
class TestAlreadyCompletedException(id: TestScheduleId) : ConflictException("Test already completed: ${id.value}")
```

**Application**
```kotlin
data class TestScheduledPayload(val testId: UUID, val studentId: UUID, val testType: String, val scheduledAt: Instant)

class ScheduleTestUseCase(
    private val testScheduleRepository: TestScheduleRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(studentId: UUID, testType: TestType, scheduledAt: Instant, location: String?, examinerId: UUID?): TestSchedule {
        val test = testScheduleRepository.save(TestSchedule(TestScheduleId(UUID.randomUUID()), studentId, testType,
            scheduledAt, location, examinerId))
        eventPublisher.publish(EventEnvelope(eventType = "test.scheduled", source = "test-service",
            payload = TestScheduledPayload(test.id.value, studentId, testType.name, scheduledAt)))
        return test
    }
}

class RescheduleTestUseCase(
    private val testScheduleRepository: TestScheduleRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(id: TestScheduleId, newTime: Instant): TestSchedule {
        val existing = testScheduleRepository.findById(id) ?: throw TestScheduleNotFoundException(id)
        if (existing.status == TestScheduleStatus.COMPLETED) throw TestAlreadyCompletedException(id)
        val updated = testScheduleRepository.save(existing.copy(scheduledAt = newTime, status = TestScheduleStatus.RESCHEDULED))
        eventPublisher.publish(EventEnvelope(eventType = "test.rescheduled", source = "test-service",
            payload = mapOf("testId" to id.value, "oldTime" to existing.scheduledAt, "newTime" to newTime)))
        return updated
    }
}

class CancelTestUseCase(
    private val testScheduleRepository: TestScheduleRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(id: TestScheduleId) {
        val existing = testScheduleRepository.findById(id) ?: throw TestScheduleNotFoundException(id)
        testScheduleRepository.updateStatus(id, TestScheduleStatus.CANCELLED)
        eventPublisher.publish(EventEnvelope(eventType = "test.cancelled", source = "test-service",
            payload = mapOf("testId" to id.value, "studentId" to existing.studentId)))
    }
}

class RecordTestResultUseCase(
    private val testScheduleRepository: TestScheduleRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(id: TestScheduleId, result: TestResult, notes: String?) {
        val existing = testScheduleRepository.findById(id) ?: throw TestScheduleNotFoundException(id)
        testScheduleRepository.recordResult(id, result, notes)
        eventPublisher.publish(EventEnvelope(eventType = "test.result.published", source = "test-service",
            payload = mapOf("testId" to id.value, "studentId" to existing.studentId, "result" to result.name)))
    }
}
class ListTestsForStudentUseCase(private val repo: TestScheduleRepository) {
    fun execute(studentId: UUID): List<TestSchedule> = repo.listByStudent(studentId)
}

data class TestReminderDuePayload(val testId: UUID, val studentId: UUID, val testType: String, val scheduledAt: Instant)

class FindDueReminderTestsUseCase(
    private val testScheduleRepository: TestScheduleRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute() {
        testScheduleRepository.findStartingWithinWithNoReminderSent(withinHours = 24).forEach { test ->
            eventPublisher.publish(EventEnvelope(eventType = "test.reminder.due", source = "test-service",
                payload = TestReminderDuePayload(test.id.value, test.studentId, test.testType.name, test.scheduledAt)))
            testScheduleRepository.markReminderSent(test.id)
        }
    }
}
```

**Infrastructure**
```kotlin
@Entity @Table(name = "test_schedules", schema = "test_schema")
class TestScheduleJpaEntity : BaseJpaEntity() {
    lateinit var studentId: UUID
    @Enumerated(EnumType.STRING) lateinit var testType: TestType
    lateinit var scheduledAt: Instant
    var location: String? = null
    var examinerOrInstructorId: UUID? = null
    @Enumerated(EnumType.STRING) var status: TestScheduleStatus = TestScheduleStatus.SCHEDULED
    @Enumerated(EnumType.STRING) var result: TestResult? = null
    var resultNotes: String? = null
    var reminderSentAt: Instant? = null
}

object TestScheduleMapper {
    fun toDomain(e: TestScheduleJpaEntity) = TestSchedule(TestScheduleId(e.id), e.studentId, e.testType, e.scheduledAt,
        e.location, e.examinerOrInstructorId, e.status, e.result, e.resultNotes, e.reminderSentAt)
    fun toEntity(t: TestSchedule) = TestScheduleJpaEntity().apply {
        id = t.id.value; studentId = t.studentId; testType = t.testType; scheduledAt = t.scheduledAt
        location = t.location; examinerOrInstructorId = t.examinerOrInstructorId; status = t.status
        result = t.result; resultNotes = t.resultNotes; reminderSentAt = t.reminderSentAt
    }
}

@ApplicationScoped
class PanacheTestScheduleRepository : TestScheduleRepository, PanacheRepositoryBase<TestScheduleJpaEntity, UUID> {
    override fun save(test: TestSchedule): TestSchedule { val e = TestScheduleMapper.toEntity(test); persist(e); return TestScheduleMapper.toDomain(e) }
    override fun findById(id: TestScheduleId) = findByIdOptional(id.value).map(TestScheduleMapper::toDomain).orElse(null)
    override fun listByStudent(studentId: UUID) = find("studentId", studentId).list().map(TestScheduleMapper::toDomain)
    override fun findStartingWithinWithNoReminderSent(withinHours: Long): List<TestSchedule> {
        val cutoff = Instant.now().plus(withinHours, ChronoUnit.HOURS)
        return find("scheduledAt <= ?1 and reminderSentAt is null and status = ?2", cutoff, TestScheduleStatus.SCHEDULED)
            .list().map(TestScheduleMapper::toDomain)
    }
    override fun markReminderSent(id: TestScheduleId) { update("reminderSentAt = ?1 where id = ?2", Instant.now(), id.value) }
    override fun updateStatus(id: TestScheduleId, status: TestScheduleStatus) { update("status = ?1 where id = ?2", status, id.value) }
    override fun recordResult(id: TestScheduleId, result: TestResult, notes: String?) {
        update("result = ?1, resultNotes = ?2, status = ?3 where id = ?4", result, notes, TestScheduleStatus.COMPLETED, id.value)
    }
}

@ApplicationScoped
class RabbitMqEventPublisher(@Channel("ds-events-out") emitter: Emitter<EventEnvelope<*>>) :
    AbstractRabbitMqEventPublisher(emitter)

@ApplicationScoped
class TestReminderJob(private val findDueReminderTestsUseCase: FindDueReminderTestsUseCase) {
    @Scheduled(every = "15m") @RunOnVirtualThread
    fun run() = findDueReminderTestsUseCase.execute()
}
```

**Presentation:** `TestResource` + DTOs, `TestExceptionMapper : BaseExceptionMapper()`.

**Tables:** `test_schedules` in `test_schema` (DDL §7).

---

### 4.5 payment-service

**Domain**
```kotlin
@JvmInline value class InvoiceId(val value: UUID)
@JvmInline value class PaymentId(val value: UUID)
enum class InvoiceStatus { PENDING, PAID, OVERDUE, CANCELLED }

data class Invoice(val id: InvoiceId, val studentId: UUID, val enrollmentId: UUID?, val amount: BigDecimal,
    val dueDate: LocalDate, val status: InvoiceStatus = InvoiceStatus.PENDING, val reminderSentAt: Instant? = null)

data class Payment(val id: PaymentId, val invoiceId: InvoiceId, val amount: BigDecimal, val paidAt: Instant, val method: String)

interface InvoiceRepository {
    fun save(invoice: Invoice): Invoice
    fun findById(id: InvoiceId): Invoice?
    fun findByIdForUpdate(id: InvoiceId): Invoice?
    fun listByStudent(studentId: UUID): List<Invoice>
    fun findDueReminders(withinDays: Long): List<Invoice>
    fun markReminderSent(id: InvoiceId)
    fun markOverdue(asOf: LocalDate): List<Invoice>
    fun updateStatus(id: InvoiceId, status: InvoiceStatus)
}
interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun sumPaidForInvoice(invoiceId: InvoiceId): BigDecimal
}

class InvoiceNotFoundException(id: InvoiceId) : NotFoundException("Invoice not found: ${id.value}")
class InvoiceAlreadyPaidException(id: InvoiceId) : ConflictException("Invoice already paid: ${id.value}")
class InvoiceCancelledException(id: InvoiceId) : ConflictException("Invoice is cancelled: ${id.value}")
class OverpaymentException(id: InvoiceId) : ValidationException("Payment exceeds remaining balance for invoice ${id.value}")
```

**Application**
```kotlin
data class InvoiceCreatedPayload(val invoiceId: UUID, val studentId: UUID, val amount: BigDecimal, val dueDate: LocalDate)

class CreateInvoiceUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(studentId: UUID, enrollmentId: UUID?, amount: BigDecimal, dueDate: LocalDate): Invoice {
        val invoice = invoiceRepository.save(Invoice(InvoiceId(UUID.randomUUID()), studentId, enrollmentId, amount, dueDate))
        eventPublisher.publish(EventEnvelope(eventType = "payment.invoice.created", source = "payment-service",
            payload = InvoiceCreatedPayload(invoice.id.value, studentId, amount, dueDate)))
        return invoice
    }
}

// Triggered by the RabbitMQ consumer on course.enrollment.created — same use case whether called from REST or a listener
class GenerateInvoiceOnEnrollmentUseCase(private val createInvoiceUseCase: CreateInvoiceUseCase) {
    fun execute(payload: EnrollmentCreatedPayload) {
        createInvoiceUseCase.execute(payload.studentId, payload.enrollmentId, payload.coursePrice, LocalDate.now().plusDays(14))
    }
}
data class EnrollmentCreatedPayload(val enrollmentId: UUID, val studentId: UUID, val batchId: UUID, val courseId: UUID, val coursePrice: BigDecimal)

data class PaymentReceivedPayload(val invoiceId: UUID, val studentId: UUID, val amount: BigDecimal, val paidAt: Instant)

class RecordPaymentUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: PaymentRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(invoiceId: InvoiceId, amount: BigDecimal, method: String): Payment {
        val invoice = invoiceRepository.findByIdForUpdate(invoiceId) ?: throw InvoiceNotFoundException(invoiceId)
        if (invoice.status == InvoiceStatus.CANCELLED) throw InvoiceCancelledException(invoiceId)
        if (invoice.status == InvoiceStatus.PAID) throw InvoiceAlreadyPaidException(invoiceId)

        val alreadyPaid = paymentRepository.sumPaidForInvoice(invoiceId)
        if (alreadyPaid.add(amount) > invoice.amount) throw OverpaymentException(invoiceId)

        val payment = paymentRepository.save(Payment(PaymentId(UUID.randomUUID()), invoiceId, amount, Instant.now(), method))
        if (alreadyPaid.add(amount) == invoice.amount) invoiceRepository.updateStatus(invoiceId, InvoiceStatus.PAID)

        eventPublisher.publish(EventEnvelope(eventType = "payment.received", source = "payment-service",
            payload = PaymentReceivedPayload(invoiceId.value, invoice.studentId, amount, payment.paidAt)))
        return payment
    }
}

class CancelInvoiceUseCase(private val repo: InvoiceRepository) {
    fun execute(id: InvoiceId) {
        repo.findById(id) ?: throw InvoiceNotFoundException(id)
        repo.updateStatus(id, InvoiceStatus.CANCELLED)
    }
}
class ListInvoicesForStudentUseCase(private val repo: InvoiceRepository) {
    fun execute(studentId: UUID): List<Invoice> = repo.listByStudent(studentId)
}

data class PaymentReminderDuePayload(val invoiceId: UUID, val studentId: UUID, val amount: BigDecimal, val dueDate: LocalDate)

class FindDueReminderInvoicesUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute() {
        invoiceRepository.findDueReminders(withinDays = 3).forEach { invoice ->
            eventPublisher.publish(EventEnvelope(eventType = "payment.reminder.due", source = "payment-service",
                payload = PaymentReminderDuePayload(invoice.id.value, invoice.studentId, invoice.amount, invoice.dueDate)))
            invoiceRepository.markReminderSent(invoice.id)
        }
    }
}

class MarkOverdueInvoicesUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute() {
        invoiceRepository.markOverdue(LocalDate.now()).forEach { invoice ->
            eventPublisher.publish(EventEnvelope(eventType = "payment.overdue", source = "payment-service",
                payload = mapOf("invoiceId" to invoice.id.value, "studentId" to invoice.studentId,
                    "amount" to invoice.amount, "dueDate" to invoice.dueDate)))
        }
    }
}
```

**Infrastructure**
```kotlin
@Entity @Table(name = "invoices", schema = "payment_schema")
class InvoiceJpaEntity : BaseJpaEntity() {
    lateinit var studentId: UUID
    var enrollmentId: UUID? = null
    var amount: BigDecimal = BigDecimal.ZERO
    lateinit var dueDate: LocalDate
    @Enumerated(EnumType.STRING) var status: InvoiceStatus = InvoiceStatus.PENDING
    var reminderSentAt: Instant? = null
}
@Entity @Table(name = "payments", schema = "payment_schema")
class PaymentJpaEntity : BaseJpaEntity() {
    lateinit var invoiceId: UUID
    var amount: BigDecimal = BigDecimal.ZERO
    lateinit var paidAt: Instant
    lateinit var method: String
}

object InvoiceMapper {
    fun toDomain(e: InvoiceJpaEntity) = Invoice(InvoiceId(e.id), e.studentId, e.enrollmentId, e.amount, e.dueDate, e.status, e.reminderSentAt)
    fun toEntity(i: Invoice) = InvoiceJpaEntity().apply {
        id = i.id.value; studentId = i.studentId; enrollmentId = i.enrollmentId; amount = i.amount
        dueDate = i.dueDate; status = i.status; reminderSentAt = i.reminderSentAt
    }
}
object PaymentMapper {
    fun toDomain(e: PaymentJpaEntity) = Payment(PaymentId(e.id), InvoiceId(e.invoiceId), e.amount, e.paidAt, e.method)
    fun toEntity(p: Payment) = PaymentJpaEntity().apply {
        id = p.id.value; invoiceId = p.invoiceId.value; amount = p.amount; paidAt = p.paidAt; method = p.method
    }
}

@ApplicationScoped
class PanacheInvoiceRepository : InvoiceRepository, PanacheRepositoryBase<InvoiceJpaEntity, UUID> {
    override fun save(invoice: Invoice): Invoice { val e = InvoiceMapper.toEntity(invoice); persist(e); return InvoiceMapper.toDomain(e) }
    override fun findById(id: InvoiceId) = findByIdOptional(id.value).map(InvoiceMapper::toDomain).orElse(null)
    override fun findByIdForUpdate(id: InvoiceId) =
        find("id", id.value).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional<InvoiceJpaEntity>()
            .map(InvoiceMapper::toDomain).orElse(null)
    override fun listByStudent(studentId: UUID) = find("studentId", studentId).list().map(InvoiceMapper::toDomain)
    override fun findDueReminders(withinDays: Long): List<Invoice> {
        val cutoff = LocalDate.now().plusDays(withinDays)
        return find("dueDate <= ?1 and reminderSentAt is null and status = ?2", cutoff, InvoiceStatus.PENDING)
            .list().map(InvoiceMapper::toDomain)
    }
    override fun markReminderSent(id: InvoiceId) { update("reminderSentAt = ?1 where id = ?2", Instant.now(), id.value) }
    override fun markOverdue(asOf: LocalDate): List<Invoice> {
        val overdue = find("dueDate < ?1 and status = ?2", asOf, InvoiceStatus.PENDING).list().map(InvoiceMapper::toDomain)
        update("status = ?1 where dueDate < ?2 and status = ?3", InvoiceStatus.OVERDUE, asOf, InvoiceStatus.PENDING)
        return overdue
    }
    override fun updateStatus(id: InvoiceId, status: InvoiceStatus) { update("status = ?1 where id = ?2", status, id.value) }
}

@ApplicationScoped
class PanachePaymentRepository : PaymentRepository, PanacheRepositoryBase<PaymentJpaEntity, UUID> {
    override fun save(payment: Payment): Payment { val e = PaymentMapper.toEntity(payment); persist(e); return PaymentMapper.toDomain(e) }
    override fun sumPaidForInvoice(invoiceId: InvoiceId): BigDecimal =
        find("invoiceId", invoiceId.value).list().sumOf { it.amount }
}

@ApplicationScoped
class RabbitMqEventPublisher(@Channel("ds-events-out") emitter: Emitter<EventEnvelope<*>>) :
    AbstractRabbitMqEventPublisher(emitter)

@ApplicationScoped
class EnrollmentCreatedListener(private val generateInvoiceOnEnrollmentUseCase: GenerateInvoiceOnEnrollmentUseCase) {
    @Incoming("course-enrollment-events") @RunOnVirtualThread
    fun onMessage(envelope: EventEnvelope<EnrollmentCreatedPayload>) = generateInvoiceOnEnrollmentUseCase.execute(envelope.payload)
}

@ApplicationScoped
class PaymentReminderJob(
    private val findDueReminderInvoicesUseCase: FindDueReminderInvoicesUseCase,
    private val markOverdueInvoicesUseCase: MarkOverdueInvoicesUseCase
) {
    @Scheduled(every = "1h") @RunOnVirtualThread
    fun run() { findDueReminderInvoicesUseCase.execute(); markOverdueInvoicesUseCase.execute() }
}
```

**Presentation:** `InvoiceResource` + DTOs, `PaymentExceptionMapper : BaseExceptionMapper()`.

**Tables:** `invoices`, `payments` in `payment_schema` (DDL §7).

---

### 4.6 notification-preference-service

**Domain**
```kotlin
data class NotificationPreference(val userId: UUID, val eventType: NotificationEventType, val channel: NotificationChannel,
    val enabled: Boolean = true, val quietHoursStart: LocalTime? = null, val quietHoursEnd: LocalTime? = null)

interface NotificationPreferenceRepository {
    fun upsert(preference: NotificationPreference): NotificationPreference
    fun findAllForUser(userId: UUID): List<NotificationPreference>
    fun findOne(userId: UUID, eventType: NotificationEventType, channel: NotificationChannel): NotificationPreference?
}
```

**Application**
```kotlin
data class PreferenceUpdatedPayload(val userId: UUID, val eventType: String, val channel: String, val enabled: Boolean)

class UpsertPreferencesUseCase(
    private val repo: NotificationPreferenceRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(userId: UUID, preferences: List<NotificationPreference>): List<NotificationPreference> =
        preferences.map { p ->
            val saved = repo.upsert(p.copy(userId = userId))
            eventPublisher.publish(EventEnvelope(eventType = "preference.updated", source = "notification-preference-service",
                payload = PreferenceUpdatedPayload(userId, saved.eventType.name, saved.channel.name, saved.enabled)))
            saved
        }
}
class GetPreferenceMatrixUseCase(private val repo: NotificationPreferenceRepository) {
    fun execute(userId: UUID): List<NotificationPreference> = repo.findAllForUser(userId)
}
class TogglePreferenceUseCase(
    private val repo: NotificationPreferenceRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(userId: UUID, eventType: NotificationEventType, channel: NotificationChannel, enabled: Boolean): NotificationPreference {
        val saved = repo.upsert(NotificationPreference(userId, eventType, channel, enabled))
        eventPublisher.publish(EventEnvelope(eventType = "preference.updated", source = "notification-preference-service",
            payload = PreferenceUpdatedPayload(userId, eventType.name, channel.name, enabled)))
        return saved
    }
}

// Triggered by the RabbitMQ consumer on user.student.registered / user.instructor.registered
class SeedDefaultPreferencesUseCase(private val repo: NotificationPreferenceRepository) {
    fun execute(userId: UUID, role: String) {
        val relevantEvents = if (role == "STUDENT")
            listOf(NotificationEventType.APPOINTMENT_CREATED, NotificationEventType.APPOINTMENT_REMINDER,
                NotificationEventType.TEST_SCHEDULED, NotificationEventType.TEST_REMINDER, NotificationEventType.TEST_RESULT_PUBLISHED,
                NotificationEventType.PAYMENT_DUE, NotificationEventType.PAYMENT_OVERDUE)
        else listOf(NotificationEventType.APPOINTMENT_CREATED, NotificationEventType.CLASS_SESSION_SCHEDULED)

        relevantEvents.forEach { event -> repo.upsert(NotificationPreference(userId, event, NotificationChannel.EMAIL, enabled = true)) }
    }
}
```

**Infrastructure**
```kotlin
@Entity
@Table(name = "notification_preferences", schema = "notification_pref_schema")
@IdClass(NotificationPreferenceJpaEntity.Key::class)
class NotificationPreferenceJpaEntity : PanacheEntityBase {
    @Id lateinit var userId: UUID
    @Id @Enumerated(EnumType.STRING) lateinit var eventType: NotificationEventType
    @Id @Enumerated(EnumType.STRING) lateinit var channel: NotificationChannel
    var enabled: Boolean = true
    var quietHoursStart: LocalTime? = null
    var quietHoursEnd: LocalTime? = null

    data class Key(val userId: UUID = UUID.randomUUID(), val eventType: NotificationEventType = NotificationEventType.STUDENT_REGISTERED,
        val channel: NotificationChannel = NotificationChannel.EMAIL) : Serializable
}

object NotificationPreferenceMapper {
    fun toDomain(e: NotificationPreferenceJpaEntity) = NotificationPreference(e.userId, e.eventType, e.channel, e.enabled, e.quietHoursStart, e.quietHoursEnd)
    fun toEntity(p: NotificationPreference) = NotificationPreferenceJpaEntity().apply {
        userId = p.userId; eventType = p.eventType; channel = p.channel; enabled = p.enabled
        quietHoursStart = p.quietHoursStart; quietHoursEnd = p.quietHoursEnd
    }
}

@ApplicationScoped
class PanacheNotificationPreferenceRepository : NotificationPreferenceRepository,
    PanacheRepositoryBase<NotificationPreferenceJpaEntity, NotificationPreferenceJpaEntity.Key> {
    override fun upsert(preference: NotificationPreference): NotificationPreference {
        val key = NotificationPreferenceJpaEntity.Key(preference.userId, preference.eventType, preference.channel)
        val existing = findByIdOptional(key).orElse(null)
        val entity = existing ?: NotificationPreferenceMapper.toEntity(preference)
        entity.enabled = preference.enabled; entity.quietHoursStart = preference.quietHoursStart; entity.quietHoursEnd = preference.quietHoursEnd
        if (existing == null) persist(entity)
        return NotificationPreferenceMapper.toDomain(entity)
    }
    override fun findAllForUser(userId: UUID) = find("userId", userId).list().map(NotificationPreferenceMapper::toDomain)
    override fun findOne(userId: UUID, eventType: NotificationEventType, channel: NotificationChannel) =
        findByIdOptional(NotificationPreferenceJpaEntity.Key(userId, eventType, channel)).map(NotificationPreferenceMapper::toDomain).orElse(null)
}

@ApplicationScoped
class RabbitMqEventPublisher(@Channel("ds-events-out") emitter: Emitter<EventEnvelope<*>>) :
    AbstractRabbitMqEventPublisher(emitter)

@ApplicationScoped
class UserRegisteredListener(private val seedDefaultPreferencesUseCase: SeedDefaultPreferencesUseCase) {
    @Incoming("user-events") @RunOnVirtualThread
    fun onStudentRegistered(envelope: EventEnvelope<StudentRegisteredPayload>) =
        seedDefaultPreferencesUseCase.execute(envelope.payload.studentId, "STUDENT")
    @Incoming("user-events") @RunOnVirtualThread
    fun onInstructorRegistered(envelope: EventEnvelope<InstructorRegisteredPayload>) =
        seedDefaultPreferencesUseCase.execute(envelope.payload.instructorId, "INSTRUCTOR")
}
data class StudentRegisteredPayload(val studentId: UUID, val email: String, val firstName: String, val lastName: String)
data class InstructorRegisteredPayload(val instructorId: UUID, val email: String, val firstName: String, val lastName: String)
```

**Presentation:** `PreferenceResource` + DTOs, `PreferenceExceptionMapper : BaseExceptionMapper()`.

**Tables:** `notification_preferences` in `notification_pref_schema` (DDL §7).

---

### 4.7 notification-service

**Domain**
```kotlin
enum class DeliveryStatus { SENT, FAILED, SUPPRESSED_BY_PREFERENCE }
data class Recipient(val userId: UUID, val address: String)

data class NotificationLogEntry(val id: UUID, val userId: UUID, val sourceEventId: UUID, val eventType: NotificationEventType,
    val channel: NotificationChannel, val status: DeliveryStatus, val providerMessageId: String?, val sentAt: Instant, val errorMessage: String?)
data class PreferenceCacheEntry(val userId: UUID, val eventType: NotificationEventType, val channel: NotificationChannel, val enabled: Boolean)
data class NotificationTemplate(val id: UUID, val eventType: NotificationEventType, val channel: NotificationChannel,
    val subjectTemplate: String?, val bodyTemplate: String) {
    fun render(recipient: Recipient): String = bodyTemplate.replace("{{recipient}}", recipient.address) // real impl uses Qute
}
data class SendResult(val success: Boolean, val providerMessageId: String?, val error: String?)

interface NotificationLogRepository {
    fun existsFor(sourceEventId: UUID, channel: NotificationChannel): Boolean
    fun save(entry: NotificationLogEntry): NotificationLogEntry
    fun history(userId: UUID?, eventType: NotificationEventType?, from: Instant?, to: Instant?): List<NotificationLogEntry>
}
interface PreferenceCacheRepository {
    fun isEnabled(userId: UUID, eventType: NotificationEventType, channel: NotificationChannel): Boolean
    fun upsert(entry: PreferenceCacheEntry)
}
interface TemplateRepository {
    fun find(eventType: NotificationEventType, channel: NotificationChannel): NotificationTemplate?
    fun save(template: NotificationTemplate): NotificationTemplate
    fun list(): List<NotificationTemplate>
}
interface NotificationSender {
    fun send(channel: NotificationChannel, recipient: String, subject: String?, body: String): SendResult
}
```

**Application**
```kotlin
data class NotificationOutcomePayload(val userId: UUID, val eventType: String, val channel: String, val providerMessageId: String?, val error: String?)

class ProcessDomainEventUseCase(
    private val preferenceCacheRepository: PreferenceCacheRepository,
    private val templateRepository: TemplateRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val notificationSender: NotificationSender,
    private val eventPublisher: DomainEventPublisher
) {
    fun execute(sourceEventId: UUID, eventType: NotificationEventType, recipients: List<Recipient>) {
        recipients.forEach { recipient ->
            NotificationChannel.entries.forEach channelLoop@{ channel ->
                if (notificationLogRepository.existsFor(sourceEventId, channel)) return@channelLoop // idempotency guard

                if (!preferenceCacheRepository.isEnabled(recipient.userId, eventType, channel)) {
                    notificationLogRepository.save(NotificationLogEntry(UUID.randomUUID(), recipient.userId, sourceEventId,
                        eventType, channel, DeliveryStatus.SUPPRESSED_BY_PREFERENCE, null, Instant.now(), null))
                    return@channelLoop
                }

                val template = templateRepository.find(eventType, channel) ?: return@channelLoop
                val result = notificationSender.send(channel, recipient.address, template.subjectTemplate, template.render(recipient))

                notificationLogRepository.save(NotificationLogEntry(UUID.randomUUID(), recipient.userId, sourceEventId, eventType,
                    channel, if (result.success) DeliveryStatus.SENT else DeliveryStatus.FAILED, result.providerMessageId, Instant.now(), result.error))

                eventPublisher.publish(EventEnvelope(
                    eventType = if (result.success) "notification.sent" else "notification.failed", source = "notification-service",
                    payload = NotificationOutcomePayload(recipient.userId, eventType.name, channel.name, result.providerMessageId, result.error)
                ))
            }
        }
    }
}

class UpdatePreferenceCacheUseCase(private val repo: PreferenceCacheRepository) {
    fun execute(userId: UUID, eventType: NotificationEventType, channel: NotificationChannel, enabled: Boolean) =
        repo.upsert(PreferenceCacheEntry(userId, eventType, channel, enabled))
}
class GetNotificationHistoryUseCase(private val repo: NotificationLogRepository) {
    fun execute(userId: UUID?, eventType: NotificationEventType?, from: Instant?, to: Instant?) = repo.history(userId, eventType, from, to)
}
class UpdateTemplateUseCase(private val repo: TemplateRepository) {
    fun execute(template: NotificationTemplate): NotificationTemplate = repo.save(template)
}
class ListTemplatesUseCase(private val repo: TemplateRepository) {
    fun execute(): List<NotificationTemplate> = repo.list()
}
```

**Infrastructure**
```kotlin
@Entity @Table(name = "notification_log", schema = "notification_schema")
class NotificationLogJpaEntity : BaseJpaEntity() {
    lateinit var userId: UUID
    lateinit var sourceEventId: UUID
    @Enumerated(EnumType.STRING) lateinit var eventType: NotificationEventType
    @Enumerated(EnumType.STRING) lateinit var channel: NotificationChannel
    @Enumerated(EnumType.STRING) lateinit var status: DeliveryStatus
    var providerMessageId: String? = null
    lateinit var sentAt: Instant
    var errorMessage: String? = null
}
@Entity
@Table(name = "preference_cache", schema = "notification_schema")
@IdClass(PreferenceCacheJpaEntity.Key::class)
class PreferenceCacheJpaEntity : PanacheEntityBase {
    @Id lateinit var userId: UUID
    @Id @Enumerated(EnumType.STRING) lateinit var eventType: NotificationEventType
    @Id @Enumerated(EnumType.STRING) lateinit var channel: NotificationChannel
    var enabled: Boolean = true
    data class Key(val userId: UUID = UUID.randomUUID(), val eventType: NotificationEventType = NotificationEventType.STUDENT_REGISTERED,
        val channel: NotificationChannel = NotificationChannel.EMAIL) : Serializable
}
@Entity @Table(name = "templates", schema = "notification_schema")
class TemplateJpaEntity : BaseJpaEntity() {
    @Enumerated(EnumType.STRING) lateinit var eventType: NotificationEventType
    @Enumerated(EnumType.STRING) lateinit var channel: NotificationChannel
    var subjectTemplate: String? = null
    lateinit var bodyTemplate: String
}

object NotificationLogMapper {
    fun toDomain(e: NotificationLogJpaEntity) = NotificationLogEntry(e.id, e.userId, e.sourceEventId, e.eventType, e.channel, e.status, e.providerMessageId, e.sentAt, e.errorMessage)
    fun toEntity(l: NotificationLogEntry) = NotificationLogJpaEntity().apply {
        id = l.id; userId = l.userId; sourceEventId = l.sourceEventId; eventType = l.eventType; channel = l.channel
        status = l.status; providerMessageId = l.providerMessageId; sentAt = l.sentAt; errorMessage = l.errorMessage
    }
}
object TemplateMapper {
    fun toDomain(e: TemplateJpaEntity) = NotificationTemplate(e.id, e.eventType, e.channel, e.subjectTemplate, e.bodyTemplate)
    fun toEntity(t: NotificationTemplate) = TemplateJpaEntity().apply {
        id = t.id; eventType = t.eventType; channel = t.channel; subjectTemplate = t.subjectTemplate; bodyTemplate = t.bodyTemplate
    }
}

@ApplicationScoped
class PanacheNotificationLogRepository : NotificationLogRepository, PanacheRepositoryBase<NotificationLogJpaEntity, UUID> {
    override fun existsFor(sourceEventId: UUID, channel: NotificationChannel) = count("sourceEventId = ?1 and channel = ?2", sourceEventId, channel) > 0
    override fun save(entry: NotificationLogEntry): NotificationLogEntry { val e = NotificationLogMapper.toEntity(entry); persist(e); return NotificationLogMapper.toDomain(e) }
    override fun history(userId: UUID?, eventType: NotificationEventType?, from: Instant?, to: Instant?) =
        findAll().list().map(NotificationLogMapper::toDomain) // filters composed as needed, omitted for brevity
}

@ApplicationScoped
class PanachePreferenceCacheRepository : PreferenceCacheRepository, PanacheRepositoryBase<PreferenceCacheJpaEntity, PreferenceCacheJpaEntity.Key> {
    override fun isEnabled(userId: UUID, eventType: NotificationEventType, channel: NotificationChannel): Boolean {
        val key = PreferenceCacheJpaEntity.Key(userId, eventType, channel)
        return findByIdOptional(key).map { it.enabled }.orElse(true) // default to enabled if no preference row yet
    }
    override fun upsert(entry: PreferenceCacheEntry) {
        val key = PreferenceCacheJpaEntity.Key(entry.userId, entry.eventType, entry.channel)
        val existing = findByIdOptional(key).orElse(null)
        if (existing != null) { existing.enabled = entry.enabled }
        else persist(PreferenceCacheJpaEntity().apply { userId = entry.userId; eventType = entry.eventType; channel = entry.channel; enabled = entry.enabled })
    }
}

@ApplicationScoped
class PanacheTemplateRepository : TemplateRepository, PanacheRepositoryBase<TemplateJpaEntity, UUID> {
    override fun find(eventType: NotificationEventType, channel: NotificationChannel) =
        find("eventType = ?1 and channel = ?2", eventType, channel).firstResultOptional<TemplateJpaEntity>().map(TemplateMapper::toDomain).orElse(null)
    override fun save(template: NotificationTemplate): NotificationTemplate { val e = TemplateMapper.toEntity(template); persist(e); return TemplateMapper.toDomain(e) }
    override fun list() = findAll().list().map(TemplateMapper::toDomain)
}

@ApplicationScoped
class CompositeNotificationSender(
    private val emailSender: EmailSender, private val smsSender: SmsSender, private val pushSender: PushSender
) : NotificationSender {
    override fun send(channel: NotificationChannel, recipient: String, subject: String?, body: String) = when (channel) {
        NotificationChannel.EMAIL -> emailSender.send(recipient, subject, body)
        NotificationChannel.SMS -> smsSender.send(recipient, body)
        NotificationChannel.PUSH -> pushSender.send(recipient, body)
    }
}
@ApplicationScoped class EmailSender { fun send(to: String, subject: String?, body: String): SendResult = SendResult(true, UUID.randomUUID().toString(), null) /* SES/SendGrid client */ }
@ApplicationScoped class SmsSender { fun send(to: String, body: String): SendResult = SendResult(true, UUID.randomUUID().toString(), null) /* Twilio client */ }
@ApplicationScoped class PushSender { fun send(to: String, body: String): SendResult = SendResult(true, UUID.randomUUID().toString(), null) /* FCM client */ }

@ApplicationScoped
class RabbitMqEventPublisher(@Channel("ds-events-out") emitter: Emitter<EventEnvelope<*>>) :
    AbstractRabbitMqEventPublisher(emitter)

@ApplicationScoped
class DomainEventListener(private val processDomainEventUseCase: ProcessDomainEventUseCase) {
    @Incoming("appointment-events") @RunOnVirtualThread
    fun onAppointmentEvent(envelope: EventEnvelope<Map<String, Any>>) {
        val (eventType, recipients) = DomainEventMapping.map(envelope) // infra-only routing-key -> recipients mapping
        processDomainEventUseCase.execute(envelope.eventId, eventType, recipients)
    }
    @Incoming("test-events") @RunOnVirtualThread
    fun onTestEvent(envelope: EventEnvelope<Map<String, Any>>) {
        val (eventType, recipients) = DomainEventMapping.map(envelope)
        processDomainEventUseCase.execute(envelope.eventId, eventType, recipients)
    }
    @Incoming("payment-events") @RunOnVirtualThread
    fun onPaymentEvent(envelope: EventEnvelope<Map<String, Any>>) {
        val (eventType, recipients) = DomainEventMapping.map(envelope)
        processDomainEventUseCase.execute(envelope.eventId, eventType, recipients)
    }
    @Incoming("class-events") @RunOnVirtualThread
    fun onClassEvent(envelope: EventEnvelope<Map<String, Any>>) {
        val (eventType, recipients) = DomainEventMapping.map(envelope)
        processDomainEventUseCase.execute(envelope.eventId, eventType, recipients)
    }
}
object DomainEventMapping {
    fun map(envelope: EventEnvelope<Map<String, Any>>): Pair<NotificationEventType, List<Recipient>> {
        // routing-key -> NotificationEventType + recipient extraction lives here, kept out of the use case deliberately
        TODO("maps envelope.eventType string + payload fields to (NotificationEventType, recipients)")
    }
}

@ApplicationScoped
class PreferenceUpdatedListener(private val updatePreferenceCacheUseCase: UpdatePreferenceCacheUseCase) {
    @Incoming("preference-updates") @RunOnVirtualThread
    fun onMessage(envelope: EventEnvelope<PreferenceUpdatedPayload>) {
        val p = envelope.payload
        updatePreferenceCacheUseCase.execute(p.userId, NotificationEventType.valueOf(p.eventType), NotificationChannel.valueOf(p.channel), p.enabled)
    }
}
```

**Presentation:** `NotificationHistoryResource`, `TemplateResource` + DTOs, `NotificationExceptionMapper : BaseExceptionMapper()`.

**Tables:** `notification_log`, `preference_cache`, `templates` in `notification_schema` (DDL §7).

---

## 5. API Gateway

Unchanged — Kong or Traefik, edge-only, path-routes to each service, no business logic:
```
/api/v1/students/**      -> identity-service        /api/v1/appointments/**  -> appointment-service
/api/v1/instructors/**   -> identity-service         /api/v1/tests/**         -> test-service
/api/v1/auth/**          -> identity-service         /api/v1/invoices/**      -> payment-service
/api/v1/courses/**       -> course-service           /api/v1/users/*/preferences/** -> notification-preference-service
/api/v1/batches/**       -> course-service           /api/v1/notifications/** -> notification-service
/api/v1/enrollments/**   -> course-service           /api/v1/templates/**     -> notification-service
/api/v1/availability/**  -> appointment-service
```

---

## 6. Eventing / RabbitMQ Topology

### 6.1 Exchanges
| Exchange | Type | Purpose |
|---|---|---|
| `ds.events` | topic | All domain events |
| `ds.dlx` | fanout | Dead-letter target |

### 6.2 Routing key catalog (payload classes shown in each service section above)
| Routing key | Publisher | Payload class |
|---|---|---|
| `user.student.registered` | identity-service | `StudentRegisteredPayload` |
| `user.instructor.registered` | identity-service | `InstructorRegisteredPayload` |
| `user.status.changed` | identity-service | `UserStatusChangedPayload` |
| `class.batch.created` | course-service | `BatchCreatedPayload` |
| `class.session.scheduled` | course-service | `SessionScheduledPayload` |
| `class.session.rescheduled` | course-service | `SessionRescheduledPayload` |
| `class.session.cancelled` | course-service | `Map` (sessionId, batchId) |
| `course.enrollment.created` | course-service | `EnrollmentCreatedPayload` (includes `coursePrice` so payment-service never needs a sync call) |
| `course.enrollment.withdrawn` | course-service | `Map` (enrollmentId, studentId, batchId) |
| `appointment.created` | appointment-service | `AppointmentCreatedPayload` |
| `appointment.rescheduled` | appointment-service | `Map` (appointmentId, oldStart, newStart) |
| `appointment.cancelled` | appointment-service | `Map` (appointmentId, studentId) |
| `appointment.reminder.due` | appointment-service | `AppointmentReminderDuePayload` |
| `test.scheduled` | test-service | `TestScheduledPayload` |
| `test.rescheduled` | test-service | `Map` (testId, oldTime, newTime) |
| `test.cancelled` | test-service | `Map` (testId, studentId) |
| `test.reminder.due` | test-service | `TestReminderDuePayload` |
| `test.result.published` | test-service | `Map` (testId, studentId, result) |
| `payment.invoice.created` | payment-service | `InvoiceCreatedPayload` |
| `payment.received` | payment-service | `PaymentReceivedPayload` |
| `payment.reminder.due` | payment-service | `PaymentReminderDuePayload` |
| `payment.overdue` | payment-service | `Map` (invoiceId, studentId, amount, dueDate) |
| `preference.updated` | notification-preference-service | `PreferenceUpdatedPayload` |
| `notification.sent` / `notification.failed` | notification-service | `NotificationOutcomePayload` |

### 6.3 Queues & bindings
| Queue | Bound to | Consumer |
|---|---|---|
| `notif.q.user-events` | `user.#` | notification-service, notification-preference-service |
| `notif.q.class-events` | `class.#`, `course.enrollment.#` | notification-service |
| `notif.q.appointment-events` | `appointment.#` | notification-service |
| `notif.q.test-events` | `test.#` | notification-service |
| `notif.q.payment-events` | `payment.#` | notification-service |
| `notif.q.preference-updates` | `preference.updated` | notification-service |
| `payment.q.enrollment-events` | `course.enrollment.created` | payment-service |

Each queue: `x-dead-letter-exchange: ds.dlx`, tuned `x-message-ttl`, with a `ds.dlq.catch-all` queue for anything that repeatedly fails.

---

## 7. Database Setup Plan

### 7.1 Provisioning steps, in order

1. **Provision one PostgreSQL instance** (managed — RDS smallest tier / DigitalOcean Managed Postgres / Neon to start) and create a single database, e.g. `driving_school`.
2. **Run the bootstrap script once** (below) — creates the 7 schemas and 7 least-privilege login roles, one per service. This is *not* owned by any service; run it by hand, via `psql`, or as a one-off Kubernetes `Job`/Helm pre-install hook. Store the generated passwords as Kubernetes Secrets referenced by each service's Helm values.
3. **Deploy each service.** On startup, Flyway (`quarkus.flyway.migrate-at-start=true`) runs that service's own migrations against its own schema only — this is what actually creates the tables (DDL in §7.3). No manual `CREATE TABLE` beyond the bootstrap step.
4. **Seed reference data** — notification-service's `templates` table needs at least one row per `(eventType, channel)` pair you intend to send; ship this as a migration too (`V2__seed_default_templates.sql`), not application code.
5. Services can be deployed in any order — there are no cross-schema foreign keys (by design, per §7 of the earlier "storage strategy" note), so within a service Flyway's own version ordering is the only ordering that matters (e.g. `user_accounts` before `student_profiles` inside identity-service).

### 7.2 Bootstrap script — schemas & roles

```sql
-- db-bootstrap/00_bootstrap_roles_and_schemas.sql
-- Run once against the `driving_school` database, by an operator/CI job — never by a service itself.

CREATE SCHEMA IF NOT EXISTS identity_schema;
CREATE SCHEMA IF NOT EXISTS course_schema;
CREATE SCHEMA IF NOT EXISTS appointment_schema;
CREATE SCHEMA IF NOT EXISTS test_schema;
CREATE SCHEMA IF NOT EXISTS payment_schema;
CREATE SCHEMA IF NOT EXISTS notification_pref_schema;
CREATE SCHEMA IF NOT EXISTS notification_schema;

CREATE ROLE identity_role LOGIN PASSWORD :'identity_pw';
CREATE ROLE course_role LOGIN PASSWORD :'course_pw';
CREATE ROLE appointment_role LOGIN PASSWORD :'appointment_pw';
CREATE ROLE test_role LOGIN PASSWORD :'test_pw';
CREATE ROLE payment_role LOGIN PASSWORD :'payment_pw';
CREATE ROLE notification_pref_role LOGIN PASSWORD :'notification_pref_pw';
CREATE ROLE notification_role LOGIN PASSWORD :'notification_pw';

GRANT ALL PRIVILEGES ON SCHEMA identity_schema TO identity_role;
GRANT ALL PRIVILEGES ON SCHEMA course_schema TO course_role;
GRANT ALL PRIVILEGES ON SCHEMA appointment_schema TO appointment_role;
GRANT ALL PRIVILEGES ON SCHEMA test_schema TO test_role;
GRANT ALL PRIVILEGES ON SCHEMA payment_schema TO payment_role;
GRANT ALL PRIVILEGES ON SCHEMA notification_pref_schema TO notification_pref_role;
GRANT ALL PRIVILEGES ON SCHEMA notification_schema TO notification_role;

-- default future tables in each schema are usable by their owning role without extra grants
ALTER DEFAULT PRIVILEGES IN SCHEMA identity_schema GRANT ALL ON TABLES TO identity_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA course_schema GRANT ALL ON TABLES TO course_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA appointment_schema GRANT ALL ON TABLES TO appointment_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA test_schema GRANT ALL ON TABLES TO test_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA payment_schema GRANT ALL ON TABLES TO payment_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA notification_pref_schema GRANT ALL ON TABLES TO notification_pref_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA notification_schema GRANT ALL ON TABLES TO notification_role;

-- each role's search_path defaults to its own schema, so a bug can't accidentally reach another schema unqualified
ALTER ROLE identity_role SET search_path = identity_schema;
ALTER ROLE course_role SET search_path = course_schema;
ALTER ROLE appointment_role SET search_path = appointment_schema;
ALTER ROLE test_role SET search_path = test_schema;
ALTER ROLE payment_role SET search_path = payment_schema;
ALTER ROLE notification_pref_role SET search_path = notification_pref_schema;
ALTER ROLE notification_role SET search_path = notification_schema;
```

Run it with actual secrets injected, e.g.:
```bash
psql -h <host> -U postgres -d driving_school \
  -v identity_pw="$(openssl rand -base64 24)" \
  -v course_pw="$(openssl rand -base64 24)" \
  -v appointment_pw="$(openssl rand -base64 24)" \
  -v test_pw="$(openssl rand -base64 24)" \
  -v payment_pw="$(openssl rand -base64 24)" \
  -v notification_pref_pw="$(openssl rand -base64 24)" \
  -v notification_pw="$(openssl rand -base64 24)" \
  -f db-bootstrap/00_bootstrap_roles_and_schemas.sql
```
Capture each generated password into its service's Kubernetes Secret (`kubectl create secret generic identity-db-credentials --from-literal=password=...`) referenced by that service's Helm `values.yaml`.

### 7.3 Per-service Flyway migrations (the actual table DDL)

Each service owns `src/main/resources/db/migration/V1__init_<service>_schema.sql`, applied automatically by Quarkus Flyway on startup against that service's own schema.

**identity-service** — `identity_schema`
```sql
CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','INSTRUCTOR','STUDENT')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE student_profiles (
    user_id UUID PRIMARY KEY REFERENCES user_accounts(id),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    date_of_birth DATE,
    guardian_contact VARCHAR(100),
    license_stage VARCHAR(20) NOT NULL DEFAULT 'LEARNER' CHECK (license_stage IN ('LEARNER','PROVISIONAL','FULL'))
);

CREATE TABLE instructor_profiles (
    user_id UUID PRIMARY KEY REFERENCES user_accounts(id),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    license_cert_number VARCHAR(50),
    years_experience INT NOT NULL DEFAULT 0,
    vehicle_assigned VARCHAR(50)
);

CREATE TABLE instructor_specialties (
    user_id UUID NOT NULL REFERENCES instructor_profiles(user_id),
    specialty VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, specialty)
);

CREATE INDEX idx_student_profiles_name ON student_profiles (lower(first_name), lower(last_name));
```

**course-service** — `course_schema`
```sql
CREATE TABLE courses (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    course_type VARCHAR(20) NOT NULL CHECK (course_type IN ('THEORY','PRACTICAL','COMBO')),
    duration_hours INT NOT NULL DEFAULT 0,
    price NUMERIC(10,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE class_batches (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id),
    instructor_id UUID NOT NULL,        -- references identity-service; app-layer only, no cross-schema FK
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    capacity INT NOT NULL DEFAULT 20,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED'))
);

CREATE TABLE class_sessions (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES class_batches(id),
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    location VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED','HELD','CANCELLED'))
);

CREATE TABLE enrollments (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,           -- references identity-service; app-layer only
    batch_id UUID NOT NULL REFERENCES class_batches(id),
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','WITHDRAWN','COMPLETED'))
);

-- partial unique index: a student can re-enroll after withdrawing, but never hold two ACTIVE enrollments in the same batch
CREATE UNIQUE INDEX uq_active_enrollment ON enrollments (student_id, batch_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_enrollments_batch ON enrollments (batch_id);
CREATE INDEX idx_class_batches_instructor ON class_batches (instructor_id);
```

**appointment-service** — `appointment_schema`
```sql
CREATE TABLE instructor_availability (
    id UUID PRIMARY KEY,
    instructor_id UUID NOT NULL,
    day_of_week VARCHAR(10) NOT NULL CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);

CREATE TABLE appointments (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    instructor_id UUID NOT NULL,
    vehicle_id VARCHAR(50),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    pickup_location VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED','RESCHEDULED','CANCELLED','COMPLETED','NO_SHOW')),
    notes TEXT,
    reminder_sent_at TIMESTAMPTZ
);

CREATE INDEX idx_appointments_instructor_time ON appointments (instructor_id, start_at);
CREATE INDEX idx_appointments_student ON appointments (student_id);
CREATE INDEX idx_appointments_reminder ON appointments (start_at) WHERE reminder_sent_at IS NULL;
```

**test-service** — `test_schema`
```sql
CREATE TABLE test_schedules (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    test_type VARCHAR(20) NOT NULL CHECK (test_type IN ('MOCK_THEORY','MOCK_PRACTICAL','OFFICIAL_THEORY','OFFICIAL_ROAD')),
    scheduled_at TIMESTAMPTZ NOT NULL,
    location VARCHAR(255),
    examiner_or_instructor_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','RESCHEDULED','CANCELLED','COMPLETED')),
    result VARCHAR(10) CHECK (result IN ('PASS','FAIL','PENDING')),
    result_notes TEXT,
    reminder_sent_at TIMESTAMPTZ
);

CREATE INDEX idx_test_schedules_student ON test_schedules (student_id);
CREATE INDEX idx_test_schedules_reminder ON test_schedules (scheduled_at) WHERE reminder_sent_at IS NULL;
```

**payment-service** — `payment_schema`
```sql
CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    enrollment_id UUID,
    amount NUMERIC(10,2) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID','OVERDUE','CANCELLED')),
    reminder_sent_at TIMESTAMPTZ
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    amount NUMERIC(10,2) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL,
    method VARCHAR(20) NOT NULL
);

CREATE INDEX idx_invoices_student ON invoices (student_id);
CREATE INDEX idx_invoices_due ON invoices (due_date) WHERE status IN ('PENDING','OVERDUE');
```

**notification-preference-service** — `notification_pref_schema`
```sql
CREATE TABLE notification_preferences (
    user_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH')),
    enabled BOOLEAN NOT NULL DEFAULT true,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    PRIMARY KEY (user_id, event_type, channel)
);
```

**notification-service** — `notification_schema`
```sql
CREATE TABLE notification_log (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_event_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('SENT','FAILED','SUPPRESSED_BY_PREFERENCE')),
    provider_message_id VARCHAR(100),
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    error_message TEXT,
    UNIQUE (source_event_id, channel)      -- backs the idempotency check in ProcessDomainEventUseCase
);

CREATE TABLE preference_cache (
    user_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(10) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (user_id, event_type, channel)
);

CREATE TABLE templates (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(10) NOT NULL,
    subject_template VARCHAR(255),
    body_template TEXT NOT NULL,
    UNIQUE (event_type, channel)
);
```

Then a follow-up seed migration, e.g. `V2__seed_default_templates.sql`:
```sql
INSERT INTO templates (id, event_type, channel, subject_template, body_template) VALUES
  (gen_random_uuid(), 'APPOINTMENT_REMINDER', 'EMAIL', 'Your driving lesson is coming up', 'Hi {{recipient}}, this is a reminder of your upcoming lesson.'),
  (gen_random_uuid(), 'APPOINTMENT_REMINDER', 'SMS',  NULL, 'Reminder: your driving lesson is coming up.'),
  (gen_random_uuid(), 'PAYMENT_DUE', 'EMAIL', 'Payment due', 'Hi {{recipient}}, you have a payment due soon.'),
  (gen_random_uuid(), 'TEST_REMINDER', 'EMAIL', 'Upcoming test', 'Hi {{recipient}}, your test is coming up.');
  -- add one row per (eventType, channel) combination you want to support at launch
```

Flyway config per service (`application.yml`):
```yaml
quarkus:
  flyway:
    migrate-at-start: true
    schemas: identity_schema        # (per service: course_schema, appointment_schema, etc.)
    default-schema: identity_schema
  datasource:
    username: identity_role
    password: ${IDENTITY_DB_PASSWORD}   # from the Kubernetes Secret created in §7.2
    jdbc:
      url: jdbc:postgresql://postgres:5432/driving_school
```

### 7.4 Local development

```yaml
# docker-compose.yml
version: "3.8"
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: driving_school
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports: ["5432:5432"]
    volumes:
      - ./db-bootstrap:/docker-entrypoint-initdb.d   # bootstrap script runs automatically on first container start
      - pgdata:/var/lib/postgresql/data
  rabbitmq:
    image: rabbitmq:3.13-management
    ports: ["5672:5672", "15672:15672"]
volumes:
  pgdata:
```
Postgres' official image auto-executes every `.sql` file in `/docker-entrypoint-initdb.d` the first time the container initializes its data directory — so mounting the bootstrap script there means `docker compose up` gives you fully-provisioned schemas/roles with zero manual steps locally, and each service's own Flyway migration then creates its tables the first time you run it against that container.

For automated tests, don't use this compose file at all — `quarkus-test-devservices` (Testcontainers under the hood) spins up an ephemeral Postgres per test run and Flyway runs the service's own migrations straight into its default schema; the multi-schema/multi-role bootstrap is a production/staging/local-dev concern only.

---

## 8. Deployment — Helm

Unchanged: one Helm chart per service, umbrella chart, small starting resource requests, HPA on CPU for request-driven services, **KEDA** scaling notification-service's consumers on RabbitMQ queue depth.

---

## 9. Security & Cross-Cutting

- **AuthN/AuthZ:** OIDC via Keycloak, or identity-service's own `TokenIssuer`/JWT as shown in §4.1 to start even cheaper; role/ownership checks live in the `presentation` layer, never inside use cases.
- **Observability:** `quarkus-micrometer` + Prometheus/Grafana, `quarkus-opentelemetry`, correlated via `EventEnvelope.correlationId`.
- **Virtual threads:** applied only at `presentation.rest`, `infrastructure.messaging.consumer`, and `infrastructure.scheduling` — never inside `domain`/`application`.
- **Testing split:** `domain`/`application` → plain JUnit5 with fake repositories/publisher (no Quarkus). `infrastructure` → `@QuarkusTest` + Testcontainers against real Postgres/RabbitMQ.

---

## 10. Rollout Plan

Unchanged: Phase 1 (identity, course, appointment, notification-preference, notification/email-only) → Phase 2 (test-service, payment-service, SMS/push, KEDA) → Phase 3 (split hot schemas onto dedicated Postgres instances, cluster RabbitMQ, harden the gateway tier).
