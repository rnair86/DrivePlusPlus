package org.rnair.driveplusplus.userservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.userservice.core.exceptions.EmailAlreadyRegisteredException
import org.rnair.driveplusplus.userservice.core.exceptions.MinorMissingGuardianContactException
import org.rnair.driveplusplus.userservice.core.exceptions.UserNotFoundException
import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.LicenseStage
import org.rnair.driveplusplus.userservice.core.model.StudentProfile
import org.rnair.driveplusplus.userservice.core.model.UserAccount
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.model.UserRole
import org.rnair.driveplusplus.userservice.core.repository.PasswordHasher
import org.rnair.driveplusplus.userservice.core.repository.StudentProfileRepository
import org.rnair.driveplusplus.userservice.core.repository.UserAccountRepository
import java.time.LocalDate
import java.util.UUID

class StudentUseCasesTest {
    @Test
    fun registersAdultStudentAndPublishesRegisteredEvent() {
        val command =
            RegisterStudentCommand(
                email = "student@example.com",
                password = "raw-password",
                firstName = "Ava",
                lastName = "Stone",
                phone = "555-0101",
                dateOfBirth = LocalDate.now().minusYears(19),
                guardianContact = null,
            )
        val accountSlot = slot<UserAccount>()
        val profileSlot = slot<StudentProfile>()
        val envelopeSlot = slot<EventEnvelope<*>>()
        val accountRepository = mockk<UserAccountRepository>()
        val profileRepository = mockk<StudentProfileRepository>()
        val passwordHasher = mockk<PasswordHasher>()
        val eventPublisher = mockk<DomainEventPublisher>()

        every { accountRepository.existsByEmail(command.email) } returns false
        every { passwordHasher.hash(command.password) } returns "hashed-password"
        every { accountRepository.save(capture(accountSlot)) } answers { firstArg() }
        every { profileRepository.save(capture(profileSlot)) } answers { firstArg() }
        every { eventPublisher.publish(capture(envelopeSlot)) } just runs

        val useCase = RegisterStudentUseCase(accountRepository, profileRepository, passwordHasher, eventPublisher)

        val saved = useCase.execute(command)

        assertEquals(profileSlot.captured, saved)
        assertEquals(command.email, accountSlot.captured.email)
        assertEquals("hashed-password", accountSlot.captured.passwordHash)
        assertEquals(UserRole.STUDENT, accountSlot.captured.role)
        assertEquals(AccountStatus.ACTIVE, accountSlot.captured.status)
        assertEquals(command.firstName, profileSlot.captured.firstName)
        assertEquals(command.lastName, profileSlot.captured.lastName)
        assertEquals(command.phone, profileSlot.captured.phone)
        assertEquals(command.dateOfBirth, profileSlot.captured.dateOfBirth)
        assertEquals(command.guardianContact, profileSlot.captured.guardianContact)
        assertEquals(LicenseStage.LEARNER, profileSlot.captured.licenseStage)

        val payload = envelopeSlot.captured.payload as StudentRegisteredPayload
        assertEquals(accountSlot.captured.id.value, payload.studentId)
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
    fun registersStudentWithoutBirthDateAndWithoutGuardianContact() {
        val command =
            RegisterStudentCommand(
                email = "no-dob@example.com",
                password = "raw-password",
                firstName = "Noah",
                lastName = "Carter",
                phone = null,
                dateOfBirth = null,
                guardianContact = null,
            )
        val accountRepository = mockk<UserAccountRepository>()
        val profileRepository = mockk<StudentProfileRepository>()
        val passwordHasher = mockk<PasswordHasher>()
        val eventPublisher = mockk<DomainEventPublisher>()

        every { accountRepository.existsByEmail(command.email) } returns false
        every { passwordHasher.hash(command.password) } returns "hashed-password"
        every { accountRepository.save(any()) } answers { firstArg() }
        every { profileRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publish(any()) } just runs

        val useCase = RegisterStudentUseCase(accountRepository, profileRepository, passwordHasher, eventPublisher)

        val saved = useCase.execute(command)

        assertEquals(command.firstName, saved.firstName)
        assertEquals(command.lastName, saved.lastName)
        assertEquals(command.phone, saved.phone)
        assertEquals(command.dateOfBirth, saved.dateOfBirth)
        assertEquals(command.guardianContact, saved.guardianContact)
        assertTrue(saved.isMinor().not())

        verify(exactly = 1) { accountRepository.existsByEmail(command.email) }
        verify(exactly = 1) { passwordHasher.hash(command.password) }
        verify(exactly = 1) { profileRepository.save(any()) }
        verify(exactly = 1) { eventPublisher.publish(any()) }
    }

    @Test
    fun throwsWhenStudentEmailIsAlreadyRegistered() {
        val command =
            RegisterStudentCommand(
                email = "duplicate@example.com",
                password = "raw-password",
                firstName = "Mia",
                lastName = "Hill",
                phone = null,
                dateOfBirth = LocalDate.now().minusYears(20),
                guardianContact = null,
            )
        val accountRepository = mockk<UserAccountRepository>()
        val profileRepository = mockk<StudentProfileRepository>()
        val passwordHasher = mockk<PasswordHasher>()
        val eventPublisher = mockk<DomainEventPublisher>()

        every { accountRepository.existsByEmail(command.email) } returns true

        val useCase = RegisterStudentUseCase(accountRepository, profileRepository, passwordHasher, eventPublisher)

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
    fun throwsWhenMinorStudentHasNoGuardianContact() {
        val command =
            RegisterStudentCommand(
                email = "minor@example.com",
                password = "raw-password",
                firstName = "Liam",
                lastName = "Reed",
                phone = "555-0202",
                dateOfBirth = LocalDate.now().minusYears(17),
                guardianContact = null,
            )
        val accountRepository = mockk<UserAccountRepository>()
        val profileRepository = mockk<StudentProfileRepository>()
        val passwordHasher = mockk<PasswordHasher>()
        val eventPublisher = mockk<DomainEventPublisher>()

        every { accountRepository.existsByEmail(command.email) } returns false
        every { passwordHasher.hash(command.password) } returns "hashed-password"
        every { accountRepository.save(any()) } answers { firstArg() }

        val useCase = RegisterStudentUseCase(accountRepository, profileRepository, passwordHasher, eventPublisher)

        assertThrows<MinorMissingGuardianContactException> {
            useCase.execute(command)
        }

        verify(exactly = 1) { accountRepository.existsByEmail(command.email) }
        verify(exactly = 1) { passwordHasher.hash(command.password) }
        verify(exactly = 1) { accountRepository.save(any()) }
        verify(exactly = 0) { profileRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun returnsStudentProfileWhenStudentExists() {
        val id = UserId(UUID.fromString("00000000-0000-0000-0000-000000001001"))
        val expected = studentProfile(id, "Sophia", "Ng", "555-0303", LocalDate.now().minusYears(18), "guardian@example.com")
        val repo = mockk<StudentProfileRepository>()

        every { repo.findByUserId(id) } returns expected

        val useCase = GetStudentUseCase(repo)

        val result = useCase.execute(id)

        assertEquals(expected, result)
        verify(exactly = 1) { repo.findByUserId(id) }
    }

    @Test
    fun throwsWhenStudentDoesNotExist() {
        val id = UserId(UUID.fromString("00000000-0000-0000-0000-000000001002"))
        val repo = mockk<StudentProfileRepository>()

        every { repo.findByUserId(id) } returns null

        val useCase = GetStudentUseCase(repo)

        assertThrows<UserNotFoundException> {
            useCase.execute(id)
        }

        verify(exactly = 1) { repo.findByUserId(id) }
    }

    @Test
    fun searchesStudentsUsingTheProvidedQueryAndPage() {
        val page = PageRequest(page = 2, size = 10)
        val query = "soph"
        val expected =
            PageResult(
                items =
                    listOf(
                        studentProfile(UserId(UUID.fromString("00000000-0000-0000-0000-000000001003")), "Sophia", "Ng", null, null, null),
                    ),
                page = page.page,
                size = page.size,
                totalElements = 1,
            )
        val repo = mockk<StudentProfileRepository>()

        every { repo.search(query, page) } returns expected

        val useCase = SearchStudentsUseCase(repo)

        val result = useCase.execute(query, page)

        assertEquals(expected, result)
        verify(exactly = 1) { repo.search(query, page) }
    }

    @Test
    fun updatesStudentProfile() {
        val profile =
            studentProfile(
                UserId(UUID.fromString("00000000-0000-0000-0000-000000001004")),
                "Ethan",
                "Park",
                "555-0404",
                LocalDate.now().minusYears(18),
                null,
            )
        val repo = mockk<StudentProfileRepository>()

        every { repo.save(profile) } returns profile

        val useCase = UpdateStudentProfileUseCase(repo)

        val result = useCase.execute(profile)

        assertEquals(profile, result)
        verify(exactly = 1) { repo.save(profile) }
    }

    private fun studentProfile(
        userId: UserId,
        firstName: String,
        lastName: String,
        phone: String?,
        dateOfBirth: LocalDate?,
        guardianContact: String?,
    ) = StudentProfile(
        userId = userId,
        firstName = firstName,
        lastName = lastName,
        phone = phone,
        dateOfBirth = dateOfBirth,
        guardianContact = guardianContact,
    )
}
