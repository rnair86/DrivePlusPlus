package org.rnair.driveplusplus.userservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.userservice.core.exceptions.InvalidCredentialsException
import org.rnair.driveplusplus.userservice.core.exceptions.UserNotFoundException
import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.UserAccount
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.model.UserRole
import org.rnair.driveplusplus.userservice.core.repository.PasswordHasher
import org.rnair.driveplusplus.userservice.core.repository.TokenIssuer
import org.rnair.driveplusplus.userservice.core.repository.UserAccountRepository
import java.time.Instant
import java.util.UUID

class UserUseCasesTest {
    @Test
    fun updatesAccountStatusAndPublishesStatusChangedEvent() {
        val accountId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
        val account = userAccount(accountId, "student@example.com", "stored-hash", UserRole.STUDENT, AccountStatus.ACTIVE)
        val repository = mockk<UserAccountRepository>()
        val publisher = mockk<DomainEventPublisher>()
        val envelopeSlot = slot<EventEnvelope<*>>()

        every { repository.findById(accountId) } returns account
        every { repository.updateStatus(accountId, AccountStatus.SUSPENDED) } just runs
        every { publisher.publish(capture(envelopeSlot)) } just runs

        val useCase = UpdateAccountStatusUseCase(repository, publisher)

        useCase.execute(accountId, AccountStatus.SUSPENDED)

        verify(exactly = 1) { repository.findById(accountId) }
        verify(exactly = 1) { repository.updateStatus(accountId, AccountStatus.SUSPENDED) }
        verify(exactly = 1) { publisher.publish(any()) }

        val envelope = envelopeSlot.captured
        assertEquals("user.status.changed", envelope.eventType)
        assertEquals("identity-service", envelope.source)

        val payload = envelope.payload as UserStatusChangedPayload
        assertEquals(accountId.value, payload.userId)
        assertEquals(AccountStatus.ACTIVE.name, payload.oldStatus)
        assertEquals(AccountStatus.SUSPENDED.name, payload.newStatus)
    }

    @Test
    fun throwsUserNotFoundWhenUpdatingUnknownAccount() {
        val accountId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000222"))
        val repository = mockk<UserAccountRepository>()
        val publisher = mockk<DomainEventPublisher>()

        every { repository.findById(accountId) } returns null

        val useCase = UpdateAccountStatusUseCase(repository, publisher)

        assertThrows<UserNotFoundException> {
            useCase.execute(accountId, AccountStatus.ARCHIVED)
        }

        verify(exactly = 1) { repository.findById(accountId) }
        verify(exactly = 0) { repository.updateStatus(any(), any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun returnsTokenWhenCredentialsAreValid() {
        val accountId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000333"))
        val account = userAccount(accountId, "admin@example.com", "stored-hash", UserRole.ADMIN, AccountStatus.ACTIVE)
        val repository = mockk<UserAccountRepository>()
        val hasher = mockk<PasswordHasher>()
        val issuer = mockk<TokenIssuer>()

        every { repository.findByEmail(account.email) } returns account
        every { hasher.matches("raw-password", account.passwordHash) } returns true
        every { issuer.issue(account.id, account.role) } returns "jwt-token"

        val useCase = AuthenticateUserUseCase(repository, hasher, issuer)

        val token = useCase.execute(account.email, "raw-password")

        assertEquals("jwt-token", token)
        verify(exactly = 1) { repository.findByEmail(account.email) }
        verify(exactly = 1) { hasher.matches("raw-password", account.passwordHash) }
        verify(exactly = 1) { issuer.issue(account.id, account.role) }
    }

    @Test
    fun throwsInvalidCredentialsWhenEmailDoesNotExist() {
        val repository = mockk<UserAccountRepository>()
        val hasher = mockk<PasswordHasher>()
        val issuer = mockk<TokenIssuer>()

        every { repository.findByEmail("missing@example.com") } returns null

        val useCase = AuthenticateUserUseCase(repository, hasher, issuer)

        assertThrows<InvalidCredentialsException> {
            useCase.execute("missing@example.com", "raw-password")
        }

        verify(exactly = 1) { repository.findByEmail("missing@example.com") }
        verify(exactly = 0) { hasher.matches(any(), any()) }
        verify(exactly = 0) { issuer.issue(any(), any()) }
    }

    @Test
    fun throwsInvalidCredentialsWhenPasswordDoesNotMatch() {
        val accountId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000444"))
        val account = userAccount(accountId, "instructor@example.com", "stored-hash", UserRole.INSTRUCTOR, AccountStatus.ACTIVE)
        val repository = mockk<UserAccountRepository>()
        val hasher = mockk<PasswordHasher>()
        val issuer = mockk<TokenIssuer>()

        every { repository.findByEmail(account.email) } returns account
        every { hasher.matches("wrong-password", account.passwordHash) } returns false

        val useCase = AuthenticateUserUseCase(repository, hasher, issuer)

        assertThrows<InvalidCredentialsException> {
            useCase.execute(account.email, "wrong-password")
        }

        verify(exactly = 1) { repository.findByEmail(account.email) }
        verify(exactly = 1) { hasher.matches("wrong-password", account.passwordHash) }
        verify(exactly = 0) { issuer.issue(any(), any()) }
    }

    private fun userAccount(
        id: UserId,
        email: String,
        passwordHash: String,
        role: UserRole,
        status: AccountStatus,
    ) = UserAccount(
        id = id,
        email = email,
        passwordHash = passwordHash,
        role = role,
        status = status,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
