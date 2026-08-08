package org.rnair.driveplusplus.userservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.userservice.core.exceptions.EmailAlreadyRegisteredException
import org.rnair.driveplusplus.userservice.core.exceptions.UserNotFoundException
import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.InstructorProfile
import org.rnair.driveplusplus.userservice.core.model.UserAccount
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.model.UserRole
import org.rnair.driveplusplus.userservice.core.repository.InstructorProfileRepository
import org.rnair.driveplusplus.userservice.core.repository.PasswordHasher
import org.rnair.driveplusplus.userservice.core.repository.UserAccountRepository
import java.time.Instant
import java.util.UUID

data class RegisterInstructorCommand(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val licenseCertNumber: String?,
    val yearsExperience: Int,
    val specialties: List<String>,
    val vehicleAssigned: String?,
)

data class InstructorRegisteredPayload(
    val instructorId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
)

@ApplicationScoped
class RegisterInstructorUseCase(
    private val userAccountRepository: UserAccountRepository,
    private val instructorProfileRepository: InstructorProfileRepository,
    private val passwordHasher: PasswordHasher,
    private val eventPublisher: DomainEventPublisher,
) {
    fun execute(command: RegisterInstructorCommand): InstructorProfile {
        if (userAccountRepository.existsByEmail(command.email)) throw EmailAlreadyRegisteredException(command.email)
        val account =
            userAccountRepository.save(
                UserAccount(
                    UserId(UUID.randomUUID()),
                    command.email,
                    passwordHasher.hash(command.password),
                    UserRole.INSTRUCTOR,
                    AccountStatus.ACTIVE,
                    Instant.now(),
                ),
            )
        val saved =
            instructorProfileRepository.save(
                InstructorProfile(
                    account.id,
                    command.firstName,
                    command.lastName,
                    command.phone,
                    command.licenseCertNumber,
                    command.yearsExperience,
                    command.specialties,
                    command.vehicleAssigned,
                ),
            )
        eventPublisher.publish(
            EventEnvelope(
                eventType = "user.instructor.registered",
                source = "identity-service",
                payload = InstructorRegisteredPayload(account.id.value, account.email, saved.firstName, saved.lastName),
            ),
        )
        return saved
    }
}

@ApplicationScoped
class GetInstructorUseCase(
    private val repo: InstructorProfileRepository,
) {
    fun execute(id: UserId): InstructorProfile = repo.findByUserId(id) ?: throw UserNotFoundException(id)
}

@ApplicationScoped
class ListInstructorsUseCase(
    private val repo: InstructorProfileRepository,
) {
    fun execute(page: PageRequest): PageResult<InstructorProfile> = repo.findAll(page)
}

@ApplicationScoped
class UpdateInstructorProfileUseCase(
    private val repo: InstructorProfileRepository,
) {
    fun execute(profile: InstructorProfile): InstructorProfile = repo.save(profile)
}
