package org.rnair.driveplusplus.userservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
import java.util.UUID

class InstructorUseCasesTest {
    @Test
    fun registersInstructorAndPublishesRegisteredEvent() {
        val command =
            RegisterInstructorCommand(
                email = "instructor@example.com",
                password = "raw-password",
                firstName = "Ivy",
                lastName = "Cole",
                phone = "555-1001",
                licenseCertNumber = "CERT-123",
                yearsExperience = 8,
                specialties = listOf("parallel parking", "highway driving"),
                vehicleAssigned = "CAR-42",
            )
        var savedAccount: UserAccount? = null
        var savedProfile: InstructorProfile? = null
        var publishedEnvelope: EventEnvelope<*>? = null
        val accountRepository = mockk<UserAccountRepository>()
        val profileRepository = mockk<InstructorProfileRepository>()
        val passwordHasher = mockk<PasswordHasher>()
        val eventPublisher = mockk<DomainEventPublisher>()

        every { accountRepository.existsByEmail(command.email) } returns false
        every { passwordHasher.hash(command.password) } returns "hashed-password"
        every { accountRepository.save(any()) } answers {
            savedAccount = firstArg()
            firstArg()
        }
        every { profileRepository.save(any()) } answers {
            savedProfile = firstArg()
            firstArg()
        }
        every { eventPublisher.publish(any()) } answers {
            publishedEnvelope = firstArg()
        }

        val useCase = RegisterInstructorUseCase(accountRepository, profileRepository, passwordHasher, eventPublisher)

        val result = useCase.execute(command)

        assertEquals(savedProfile, result)
        assertEquals(command.email, savedAccount!!.email)
        assertEquals("hashed-password", savedAccount!!.passwordHash)
        assertEquals(UserRole.INSTRUCTOR, savedAccount!!.role)
        assertEquals(AccountStatus.ACTIVE, savedAccount!!.status)
        assertEquals(command.firstName, savedProfile!!.firstName)
        assertEquals(command.lastName, savedProfile!!.lastName)
        assertEquals(command.phone, savedProfile!!.phone)
        assertEquals(command.licenseCertNumber, savedProfile!!.licenseCertNumber)
        assertEquals(command.yearsExperience, savedProfile!!.yearsExperience)
        assertEquals(command.specialties, savedProfile!!.specialties)
        assertEquals(command.vehicleAssigned, savedProfile!!.vehicleAssigned)

        val payload = publishedEnvelope!!.payload as InstructorRegisteredPayload
        assertEquals(savedAccount!!.id.value, payload.instructorId)
        assertEquals(command.email, payload.email)
        assertEquals(command.firstName, payload.firstName)
        assertEquals(command.lastName, payload.lastName)

        verify(exactly = 1) { accountRepository.existsByEmail(command.email) }
        verify(exactly = 1) { passwordHasher.hash(command.password) }
        verify(exactly = 1) { accountRepository.save(any()) }
        verify(exactly = 1) { profileRepository.save(any()) }
        verify(exactly = 1) { eventPublisher.publish(any()) }
    }

    @Test
    fun throwsWhenInstructorEmailIsAlreadyRegistered() {
        val command =
            RegisterInstructorCommand(
                email = "duplicate@example.com",
                password = "raw-password",
                firstName = "Noah",
                lastName = "Lane",
                phone = null,
                licenseCertNumber = null,
                yearsExperience = 3,
                specialties = emptyList(),
                vehicleAssigned = null,
            )
        val accountRepository = mockk<UserAccountRepository>()
        val profileRepository = mockk<InstructorProfileRepository>()
        val passwordHasher = mockk<PasswordHasher>()
        val eventPublisher = mockk<DomainEventPublisher>()

        every { accountRepository.existsByEmail(command.email) } returns true

        val useCase = RegisterInstructorUseCase(accountRepository, profileRepository, passwordHasher, eventPublisher)

        assertThrows<EmailAlreadyRegisteredException> {
            useCase.execute(command)
        }

        verify(exactly = 1) { accountRepository.existsByEmail(command.email) }
        verify(exactly = 0) { passwordHasher.hash(any()) }
        verify(exactly = 0) { accountRepository.save(any()) }
        verify(exactly = 0) { profileRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun returnsInstructorProfileWhenInstructorExists() {
        val id = UserId(UUID.fromString("00000000-0000-0000-0000-000000002001"))
        val expected =
            InstructorProfile(
                userId = id,
                firstName = "Ava",
                lastName = "Stone",
                phone = "555-2001",
                licenseCertNumber = "CERT-2001",
                yearsExperience = 10,
                specialties = listOf("night driving"),
                vehicleAssigned = "CAR-7",
            )
        val repo = mockk<InstructorProfileRepository>()

        every { repo.findByUserId(id) } returns expected

        val useCase = GetInstructorUseCase(repo)

        val result = useCase.execute(id)

        assertEquals(expected, result)
        verify(exactly = 1) { repo.findByUserId(id) }
    }

    @Test
    fun throwsWhenInstructorDoesNotExist() {
        val id = UserId(UUID.fromString("00000000-0000-0000-0000-000000002002"))
        val repo = mockk<InstructorProfileRepository>()

        every { repo.findByUserId(id) } returns null

        val useCase = GetInstructorUseCase(repo)

        assertThrows<UserNotFoundException> {
            useCase.execute(id)
        }

        verify(exactly = 1) { repo.findByUserId(id) }
    }

    @Test
    fun listsInstructorsUsingTheProvidedPage() {
        val page = PageRequest(page = 1, size = 25)
        val expected =
            PageResult(
                items =
                    listOf(
                        instructorProfile(
                            UserId(UUID.fromString("00000000-0000-0000-0000-000000002003")),
                            "Liam",
                            "Reed",
                        ),
                    ),
                page = page.page,
                size = page.size,
                totalElements = 1,
            )
        val repo = mockk<InstructorProfileRepository>()

        every { repo.findAll(page) } returns expected

        val useCase = ListInstructorsUseCase(repo)

        val result = useCase.execute(page)

        assertEquals(expected, result)
        verify(exactly = 1) { repo.findAll(page) }
    }

    @Test
    fun updatesInstructorProfile() {
        val profile =
            instructorProfile(
                UserId(UUID.fromString("00000000-0000-0000-0000-000000002004")),
                "Mia",
                "Cole",
            )
        val repo = mockk<InstructorProfileRepository>()

        every { repo.save(profile) } returns profile

        val useCase = UpdateInstructorProfileUseCase(repo)

        val result = useCase.execute(profile)

        assertEquals(profile, result)
        verify(exactly = 1) { repo.save(profile) }
    }

    private fun instructorProfile(
        userId: UserId,
        firstName: String,
        lastName: String,
    ) = InstructorProfile(
        userId = userId,
        firstName = firstName,
        lastName = lastName,
        phone = null,
        licenseCertNumber = null,
        yearsExperience = 0,
        specialties = emptyList(),
        vehicleAssigned = null,
    )
}
