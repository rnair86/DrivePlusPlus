package org.rnair.driveplusplus.userservice.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UserAccountTest {
    @Test
    fun usesActiveStatusByDefaultWhenNotProvided() {
        val account =
            UserAccount(
                id = UserId(UUID.randomUUID()),
                email = "user@example.com",
                passwordHash = "hashed-password",
                role = UserRole.STUDENT,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            )

        assertEquals(AccountStatus.ACTIVE, account.status)
    }

    @Test
    fun keepsAllProvidedFieldValues() {
        val id = UserId(UUID.fromString("00000000-0000-0000-0000-000000000123"))
        val createdAt = Instant.parse("2026-02-20T10:15:30Z")

        val account =
            UserAccount(
                id = id,
                email = "admin+west@example.com",
                passwordHash = $$"argon2id$abc123",
                role = UserRole.ADMIN,
                status = AccountStatus.SUSPENDED,
                createdAt = createdAt,
            )

        assertEquals(id, account.id)
        assertEquals("admin+west@example.com", account.email)
        assertEquals($$"argon2id$abc123", account.passwordHash)
        assertEquals(UserRole.ADMIN, account.role)
        assertEquals(AccountStatus.SUSPENDED, account.status)
        assertEquals(createdAt, account.createdAt)
    }

    @Test
    fun considersAccountsWithSameValuesEqual() {
        val id = UserId(UUID.fromString("00000000-0000-0000-0000-000000000456"))
        val createdAt = Instant.parse("2026-03-01T08:00:00Z")

        val first =
            UserAccount(
                id = id,
                email = "same@example.com",
                passwordHash = "hash",
                role = UserRole.INSTRUCTOR,
                status = AccountStatus.ARCHIVED,
                createdAt = createdAt,
            )

        val second =
            UserAccount(
                id = id,
                email = "same@example.com",
                passwordHash = "hash",
                role = UserRole.INSTRUCTOR,
                status = AccountStatus.ARCHIVED,
                createdAt = createdAt,
            )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun treatsAccountsWithDifferentIdsAsDifferentUsers() {
        val createdAt = Instant.parse("2026-03-01T08:00:00Z")

        val first =
            UserAccount(
                id = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                email = "same@example.com",
                passwordHash = "hash",
                role = UserRole.STUDENT,
                status = AccountStatus.ACTIVE,
                createdAt = createdAt,
            )

        val second =
            UserAccount(
                id = UserId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                email = "same@example.com",
                passwordHash = "hash",
                role = UserRole.STUDENT,
                status = AccountStatus.ACTIVE,
                createdAt = createdAt,
            )

        assertNotEquals(first, second)
    }
}
