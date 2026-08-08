package org.rnair.driveplusplus.userservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.userservice.core.exceptions.EmailAlreadyRegisteredException
import org.rnair.driveplusplus.userservice.core.exceptions.MinorMissingGuardianContactException
import org.rnair.driveplusplus.userservice.core.exceptions.UserNotFoundException
import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.StudentProfile
import org.rnair.driveplusplus.userservice.core.model.UserAccount
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.model.UserRole
import org.rnair.driveplusplus.userservice.core.repository.PasswordHasher
import org.rnair.driveplusplus.userservice.core.repository.StudentProfileRepository
import org.rnair.driveplusplus.userservice.core.repository.UserAccountRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RegisterStudentCommand(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val dateOfBirth: LocalDate?,
    val guardianContact: String?,
)

data class StudentRegisteredPayload(
    val studentId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
)

@ApplicationScoped
class RegisterStudentUseCase(
    private val userAccountRepository: UserAccountRepository,
    private val studentProfileRepository: StudentProfileRepository,
    private val passwordHasher: PasswordHasher,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(command: RegisterStudentCommand): StudentProfile {
        if (userAccountRepository.existsByEmail(command.email)) throw EmailAlreadyRegisteredException(command.email)

        val account =
            userAccountRepository.save(
                UserAccount(
                    UserId(UUID.randomUUID()),
                    command.email,
                    passwordHasher.hash(command.password),
                    UserRole.STUDENT,
                    AccountStatus.ACTIVE,
                    Instant.now(),
                ),
            )
        val profile =
            StudentProfile(
                account.id,
                command.firstName,
                command.lastName,
                command.phone,
                command.dateOfBirth,
                command.guardianContact,
            )
        if (profile.isMinor() && profile.guardianContact.isNullOrBlank()) throw MinorMissingGuardianContactException()

        val saved = studentProfileRepository.save(profile)
        eventPublisher.publish(
            EventEnvelope(
                eventType = "user.student.registered",
                source = "identity-service",
                payload = StudentRegisteredPayload(account.id.value, account.email, saved.firstName, saved.lastName),
            ),
        )
        return saved
    }
}

@ApplicationScoped
class GetStudentUseCase(
    private val repo: StudentProfileRepository,
) {
    fun execute(id: UserId): StudentProfile = repo.findByUserId(id) ?: throw UserNotFoundException(id)
}

@ApplicationScoped
class SearchStudentsUseCase(
    private val repo: StudentProfileRepository,
) {
    fun execute(
        query: String?,
        page: PageRequest,
    ): PageResult<StudentProfile> = repo.search(query, page)
}

@ApplicationScoped
class UpdateStudentProfileUseCase(
    private val repo: StudentProfileRepository,
) {
    @Transactional
    fun execute(profile: StudentProfile): StudentProfile = repo.save(profile)
}
