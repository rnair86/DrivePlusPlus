package org.rnair.driveplusplus.userservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.userservice.core.exceptions.InvalidCredentialsException
import org.rnair.driveplusplus.userservice.core.exceptions.UserNotFoundException
import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.repository.PasswordHasher
import org.rnair.driveplusplus.userservice.core.repository.TokenIssuer
import org.rnair.driveplusplus.userservice.core.repository.UserAccountRepository
import java.util.UUID

data class UserStatusChangedPayload(
    val userId: UUID,
    val oldStatus: String,
    val newStatus: String,
)

@ApplicationScoped
class UpdateAccountStatusUseCase(
    private val userAccountRepository: UserAccountRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    fun execute(
        id: UserId,
        newStatus: AccountStatus,
    ) {
        val account = userAccountRepository.findById(id) ?: throw UserNotFoundException(id)
        userAccountRepository.updateStatus(id, newStatus)
        eventPublisher.publish(
            EventEnvelope(
                eventType = "user.status.changed",
                source = "identity-service",
                payload = UserStatusChangedPayload(id.value, account.status.name, newStatus.name),
            ),
        )
    }
}

@ApplicationScoped
class AuthenticateUserUseCase(
    private val userAccountRepository: UserAccountRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
) {
    fun execute(
        email: String,
        password: String,
    ): String {
        val account = userAccountRepository.findByEmail(email) ?: throw InvalidCredentialsException()
        if (!passwordHasher.matches(password, account.passwordHash)) throw InvalidCredentialsException()
        return tokenIssuer.issue(account.id, account.role)
    }
}
