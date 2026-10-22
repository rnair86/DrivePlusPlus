package org.rnair.driveplusplus.userservice.infrastructure.repository

import com.rnair.drivingplusplus.commons.paging.PageRequest
import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.InstructorProfile
import org.rnair.driveplusplus.userservice.core.model.UserAccount
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.model.UserRole
import java.time.Instant
import java.util.UUID

@QuarkusTest
class PanacheInstructorProfileRepositoryTest {
    @Test
    @TestTransaction
    fun savesInstructorProfileAndReturnsTheStoredDomainObject() {
        val repo = PanacheInstructorProfileRepository()
        val userId = UserId(UUID.randomUUID())
        val profile =
            InstructorProfile(
                userId = userId,
                firstName = "Emma",
                lastName = "Taylor",
                phone = "555-3001",
                licenseCertNumber = "CERT-3001",
                yearsExperience = 5,
                specialties = listOf("city driving", "defensive driving"),
                vehicleAssigned = "CAR-99",
            )

        val saved = repo.save(profile)

        assertEquals(userId.value, saved.userId.value)
        assertEquals("Emma", saved.firstName)
        assertEquals("Taylor", saved.lastName)
        assertEquals("555-3001", saved.phone)
        assertEquals("CERT-3001", saved.licenseCertNumber)
        assertEquals(5, saved.yearsExperience)
        assertEquals(listOf("city driving", "defensive driving"), saved.specialties)
        assertEquals("CAR-99", saved.vehicleAssigned)
    }

    @Test
    @TestTransaction
    fun findsInstructorProfileByUserIdWhenProfileExists() {
        val repo = PanacheInstructorProfileRepository()
        val userId = UserId(UUID.randomUUID())
        val profile =
            InstructorProfile(
                userId = userId,
                firstName = "James",
                lastName = "Miller",
                phone = "555-3002",
                licenseCertNumber = "CERT-3002",
                yearsExperience = 12,
                specialties = listOf("motorway driving"),
                vehicleAssigned = "CAR-100",
            )
        repo.save(profile)

        val found = repo.findByUserId(userId)

        assertNotNull(found)
        assertEquals(userId.value, found!!.userId.value)
        assertEquals("James", found.firstName)
        assertEquals("Miller", found.lastName)
        assertEquals("555-3002", found.phone)
        assertEquals("CERT-3002", found.licenseCertNumber)
        assertEquals(12, found.yearsExperience)
        assertEquals(listOf("motorway driving"), found.specialties)
        assertEquals("CAR-100", found.vehicleAssigned)
    }

    @Test
    @TestTransaction
    fun returnsNullWhenInstructorProfileDoesNotExist() {
        val repo = PanacheInstructorProfileRepository()
        val nonExistentId = UserId(UUID.randomUUID())

        val found = repo.findByUserId(nonExistentId)

        assertNull(found)
    }

    @Test
    @TestTransaction
    fun listsAllInstructorProfilesWithFirstPage() {
        val repo = PanacheInstructorProfileRepository()
        val userId1 = UserId(UUID.randomUUID())
        val userId2 = UserId(UUID.randomUUID())
        saveInstructorAccount(userId1)
        saveInstructorAccount(userId2)
        val profile1 =
            InstructorProfile(
                userId = userId1,
                firstName = "Oliver",
                lastName = "Brown",
                phone = "555-3003",
                licenseCertNumber = "CERT-3003",
                yearsExperience = 3,
                specialties = listOf("parking"),
                vehicleAssigned = null,
            )
        val profile2 =
            InstructorProfile(
                userId = userId2,
                firstName = "Sophia",
                lastName = "Davis",
                phone = "555-3004",
                licenseCertNumber = "CERT-3004",
                yearsExperience = 7,
                specialties = listOf("highway", "city"),
                vehicleAssigned = "CAR-101",
            )
        repo.save(profile1)
        repo.save(profile2)

        val page = repo.findAll(PageRequest(page = 0, size = 20))

        assertEquals(0, page.page)
        assertEquals(20, page.size)
        assertEquals(2, page.totalElements)
        assertEquals(2, page.items.size)
    }

    @Test
    fun returnsEmptyPageWhenNoProfilesExist() {
        val repo = PanacheInstructorProfileRepository()

        val page = repo.findAll(PageRequest(page = 0, size = 20))

        assertEquals(0, page.page)
        assertEquals(20, page.size)
        assertEquals(0, page.totalElements)
        assertEquals(0, page.items.size)
    }

    @Test
    @TestTransaction
    fun savesInstructorProfileWithMinimalFields() {
        val repo = PanacheInstructorProfileRepository()
        val userId = UserId(UUID.randomUUID())
        val profile =
            InstructorProfile(
                userId = userId,
                firstName = "Alex",
                lastName = "Smith",
                phone = null,
                licenseCertNumber = null,
                yearsExperience = 0,
                specialties = emptyList(),
                vehicleAssigned = null,
            )

        val saved = repo.save(profile)

        assertEquals(userId.value, saved.userId.value)
        assertEquals("Alex", saved.firstName)
        assertEquals("Smith", saved.lastName)
        assertNull(saved.phone)
        assertNull(saved.licenseCertNumber)
        assertEquals(0, saved.yearsExperience)
        assertEquals(emptyList<String>(), saved.specialties)
        assertNull(saved.vehicleAssigned)
    }

    @Test
    @TestTransaction
    fun updatesExistingInstructorProfile() {
        val repo = PanacheInstructorProfileRepository()
        val userId = UserId(UUID.randomUUID())
        saveInstructorAccount(userId)
        val originalProfile =
            InstructorProfile(
                userId = userId,
                firstName = "Charlotte",
                lastName = "Wilson",
                phone = "555-3005",
                licenseCertNumber = "CERT-3005",
                yearsExperience = 4,
                specialties = listOf("basic"),
                vehicleAssigned = "CAR-102",
            )
        repo.save(originalProfile)

        val updatedProfile =
            InstructorProfile(
                userId = userId,
                firstName = "Charlotte",
                lastName = "Wilson",
                phone = "555-3005-NEW",
                licenseCertNumber = "CERT-3005-NEW",
                yearsExperience = 5,
                specialties = listOf("basic", "advanced"),
                vehicleAssigned = "CAR-103",
            )
        repo.save(updatedProfile)

        val found = repo.findByUserId(userId)
        assertNotNull(found)
        assertEquals("555-3005-NEW", found!!.phone)
        assertEquals("CERT-3005-NEW", found.licenseCertNumber)
        assertEquals(5, found.yearsExperience)
        assertEquals(listOf("basic", "advanced"), found.specialties)
        assertEquals("CAR-103", found.vehicleAssigned)
    }

    private fun saveInstructorAccount(userId: UserId) {
        PanacheUserAccountRepository().save(
            UserAccount(
                id = userId,
                email = "${userId.value}@example.com",
                passwordHash = "hashed-password",
                role = UserRole.INSTRUCTOR,
                status = AccountStatus.ACTIVE,
                createdAt = Instant.now(),
            ),
        )
    }
}
