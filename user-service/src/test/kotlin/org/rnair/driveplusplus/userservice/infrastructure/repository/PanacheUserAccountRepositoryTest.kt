package org.rnair.driveplusplus.userservice.infrastructure.repository
import io.quarkus.test.junit.QuarkusTest
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.UserAccount
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.model.UserRole
@QuarkusTest
class PanacheUserAccountRepositoryTest {
    @Test
    fun savesUserAccountAndReturnsTheStoredDomainObject() {
        val repo = PanacheUserAccountRepository()
        val userId = UserId(UUID.randomUUID())
        val account =
            UserAccount(
                id = userId,
                email = "user@example.com",
                passwordHash = "hashed-password",
                role = UserRole.STUDENT,
                status = AccountStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        val saved = repo.save(account)
        assertEquals(userId.value, saved.id.value)
        assertEquals("user@example.com", saved.email)
        assertEquals("hashed-password", saved.passwordHash)
        assertEquals(UserRole.STUDENT, saved.role)
        assertEquals(AccountStatus.ACTIVE, saved.status)
    }
    @Test
    fun findsUserAccountByIdWhenAccountExists() {
        val repo = PanacheUserAccountRepository()
        val userId = UserId(UUID.randomUUID())
        val account =
            UserAccount(
                id = userId,
                email = "admin@example.com",
                passwordHash = "admin-hash",
                role = UserRole.ADMIN,
                status = AccountStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        repo.save(account)
        val found = repo.findById(userId)
        assertNotNull(found)
        assertEquals(userId.value, found!!.id.value)
        assertEquals("admin@example.com", found.email)
        assertEquals(UserRole.ADMIN, found.role)
    }
    @Test
    fun returnsNullWhenUserAccountDoesNotExist() {
        val repo = PanacheUserAccountRepository()
        val nonExistentId = UserId(UUID.randomUUID())
        val found = repo.findById(nonExistentId)
        assertNull(found)
    }
    @Test
    fun findsUserAccountByEmailWhenAccountExists() {
        val repo = PanacheUserAccountRepository()
        val userId = UserId(UUID.randomUUID())
        val account =
            UserAccount(
                id = userId,
                email = "instructor@example.com",
                passwordHash = "instructor-hash",
                role = UserRole.INSTRUCTOR,
                status = AccountStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        repo.save(account)
        val found = repo.findByEmail("instructor@example.com")
        assertNotNull(found)
        assertEquals("instructor@example.com", found!!.email)
        assertEquals(UserRole.INSTRUCTOR, found.role)
    }
    @Test
    fun returnsNullWhenFindingByEmailAndAccountDoesNotExist() {
        val repo = PanacheUserAccountRepository()
        val found = repo.findByEmail("nonexistent@example.com")
        assertNull(found)
    }
    @Test
    fun returnsTrueWhenEmailExists() {
        val repo = PanacheUserAccountRepository()
        val account =
            UserAccount(
                id = UserId(UUID.randomUUID()),
                email = "existing@example.com",
                passwordHash = "hash",
                role = UserRole.STUDENT,
                status = AccountStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        repo.save(account)
        val exists = repo.existsByEmail("existing@example.com")
        assertTrue(exists)
    }
    @Test
    fun returnsFalseWhenEmailDoesNotExist() {
        val repo = PanacheUserAccountRepository()
        val exists = repo.existsByEmail("nonexistent@example.com")
        assertFalse(exists)
    }
    @Test
    fun updatesUserAccountStatus() {
        val repo = PanacheUserAccountRepository()
        val userId = UserId(UUID.randomUUID())
        val account =
            UserAccount(
                id = userId,
                email = "user@example.com",
                passwordHash = "hash",
                role = UserRole.STUDENT,
                status = AccountStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        repo.save(account)
        repo.updateStatus(userId, AccountStatus.SUSPENDED)
        val found = repo.findById(userId)
        assertNotNull(found)
        assertEquals(AccountStatus.SUSPENDED, found!!.status)
    }
    @Test
    fun returnsCorrectCountOfUserAccounts() {
        val repo = PanacheUserAccountRepository()
        val account1 =
            UserAccount(
                id = UserId(UUID.randomUUID()),
                email = "user1@example.com",
                passwordHash = "hash1",
                role = UserRole.STUDENT,
                status = AccountStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        val account2 =
            UserAccount(
                id = UserId(UUID.randomUUID()),
                email = "user2@example.com",
                passwordHash = "hash2",
                role = UserRole.INSTRUCTOR,
                status = AccountStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        repo.save(account1)
        repo.save(account2)
        val count = repo.getCount()
        assertEquals(2, count)
    }
}
