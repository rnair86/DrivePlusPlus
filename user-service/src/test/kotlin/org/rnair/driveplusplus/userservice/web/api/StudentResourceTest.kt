package org.rnair.driveplusplus.userservice.web.api

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.userservice.application.usecase.GetStudentUseCase
import org.rnair.driveplusplus.userservice.application.usecase.RegisterStudentUseCase
import org.rnair.driveplusplus.userservice.application.usecase.SearchStudentsUseCase
import org.rnair.driveplusplus.userservice.application.usecase.UpdateStudentProfileUseCase
import org.rnair.driveplusplus.userservice.core.exceptions.EmailAlreadyRegisteredException
import org.rnair.driveplusplus.userservice.core.exceptions.UserNotFoundException
import org.rnair.driveplusplus.userservice.core.model.LicenseStage
import org.rnair.driveplusplus.userservice.core.model.StudentProfile
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.web.dtos.RegisterStudentRequest

class StudentResourceTest {
    @Test
    fun registersStudentAndReturnsCreatedStatusWithResponse() {
        val registerUseCase = mockk<RegisterStudentUseCase>()
        val getUseCase = mockk<GetStudentUseCase>()
        val searchUseCase = mockk<SearchStudentsUseCase>()
        val updateUseCase = mockk<UpdateStudentProfileUseCase>()
        val request =
            RegisterStudentRequest(
                email = "student@example.com",
                password = "password",
                firstName = "Blake",
                lastName = "Harper",
                phone = "555-4001",
                dateOfBirth = LocalDate.now().minusYears(19),
                guardianContact = null,
            )
        val profile =
            StudentProfile(
                userId = UserId(UUID.fromString("00000000-0000-0000-0000-000000004001")),
                firstName = "Blake",
                lastName = "Harper",
                phone = "555-4001",
                dateOfBirth = LocalDate.now().minusYears(19),
                guardianContact = null,
                licenseStage = LicenseStage.LEARNER,
            )

        every { registerUseCase.execute(any()) } returns profile

        val resource = StudentResource(registerUseCase, getUseCase, searchUseCase, updateUseCase)

        val response = resource.register(request)

        assertEquals(201, response.status)
        verify(exactly = 1) { registerUseCase.execute(any()) }
    }

    @Test
    fun forwardsRegisterRequestFieldsToUseCase() {
        val registerUseCase = mockk<RegisterStudentUseCase>()
        val getUseCase = mockk<GetStudentUseCase>()
        val searchUseCase = mockk<SearchStudentsUseCase>()
        val updateUseCase = mockk<UpdateStudentProfileUseCase>()
        val request =
            RegisterStudentRequest(
                email = "student@example.com",
                password = "password",
                firstName = "Blake",
                lastName = "Harper",
                phone = "555-4001",
                dateOfBirth = LocalDate.now().minusYears(19),
                guardianContact = null,
            )
        val profile =
            StudentProfile(
                userId = UserId(UUID.fromString("00000000-0000-0000-0000-000000004001")),
                firstName = "Blake",
                lastName = "Harper",
                phone = "555-4001",
                dateOfBirth = LocalDate.now().minusYears(19),
                guardianContact = null,
            )

        every { registerUseCase.execute(any()) } returns profile

        val resource = StudentResource(registerUseCase, getUseCase, searchUseCase, updateUseCase)

        resource.register(request)

        verify(exactly = 1) {
            registerUseCase.execute(
                withArg {
                    assertEquals("student@example.com", it.email)
                    assertEquals("password", it.password)
                    assertEquals("Blake", it.firstName)
                    assertEquals("Harper", it.lastName)
                    assertEquals("555-4001", it.phone)
                    assertEquals(LocalDate.now().minusYears(19), it.dateOfBirth)
                    assertEquals(null, it.guardianContact)
                },
            )
        }
    }

    @Test
    fun propagatesDuplicateEmailExceptionWhenRegistering() {
        val registerUseCase = mockk<RegisterStudentUseCase>()
        val getUseCase = mockk<GetStudentUseCase>()
        val searchUseCase = mockk<SearchStudentsUseCase>()
        val updateUseCase = mockk<UpdateStudentProfileUseCase>()
        val request =
            RegisterStudentRequest(
                email = "duplicate@example.com",
                password = "password",
                firstName = "Alex",
                lastName = "Morgan",
                phone = null,
                dateOfBirth = null,
                guardianContact = null,
            )

        every { registerUseCase.execute(any()) } throws EmailAlreadyRegisteredException("duplicate@example.com")

        val resource = StudentResource(registerUseCase, getUseCase, searchUseCase, updateUseCase)

        assertThrows<EmailAlreadyRegisteredException> {
            resource.register(request)
        }

        verify(exactly = 1) { registerUseCase.execute(any()) }
    }

    @Test
    fun returnsStudentProfileWhenGettingById() {
        val registerUseCase = mockk<RegisterStudentUseCase>()
        val getUseCase = mockk<GetStudentUseCase>()
        val searchUseCase = mockk<SearchStudentsUseCase>()
        val updateUseCase = mockk<UpdateStudentProfileUseCase>()
        val userId = UUID.fromString("00000000-0000-0000-0000-000000004002")
        val profile =
            StudentProfile(
                userId = UserId(userId),
                firstName = "Casey",
                lastName = "Mitchell",
                phone = "555-4002",
                dateOfBirth = LocalDate.now().minusYears(18),
                guardianContact = "guardian@example.com",
                licenseStage = LicenseStage.LEARNER,
            )

        every { getUseCase.execute(UserId(userId)) } returns profile

        val resource = StudentResource(registerUseCase, getUseCase, searchUseCase, updateUseCase)

        val response = resource.get(userId)

        assertEquals(userId, response.userId)
        assertEquals("Casey", response.firstName)
        assertEquals("Mitchell", response.lastName)
        assertEquals("LEARNER", response.licenseStage)
        verify(exactly = 1) { getUseCase.execute(UserId(userId)) }
    }

    @Test
    fun throwsWhenGettingNonExistentStudent() {
        val registerUseCase = mockk<RegisterStudentUseCase>()
        val getUseCase = mockk<GetStudentUseCase>()
        val searchUseCase = mockk<SearchStudentsUseCase>()
        val updateUseCase = mockk<UpdateStudentProfileUseCase>()
        val userId = UUID.fromString("00000000-0000-0000-0000-000000004003")

        every { getUseCase.execute(UserId(userId)) } throws UserNotFoundException(UserId(userId))

        val resource = StudentResource(registerUseCase, getUseCase, searchUseCase, updateUseCase)

        assertThrows<UserNotFoundException> {
            resource.get(userId)
        }

        verify(exactly = 1) { getUseCase.execute(UserId(userId)) }
    }

    @Test
    fun searchesStudentsWithQueryAndPaginationParameters() {
        val registerUseCase = mockk<RegisterStudentUseCase>()
        val getUseCase = mockk<GetStudentUseCase>()
        val searchUseCase = mockk<SearchStudentsUseCase>()
        val updateUseCase = mockk<UpdateStudentProfileUseCase>()
        val expectedResult =
            PageResult<StudentProfile>(
                items = emptyList(),
                page = 1,
                size = 10,
                totalElements = 0,
            )

        every { searchUseCase.execute("casey", PageRequest(1, 10)) } returns expectedResult

        val resource = StudentResource(registerUseCase, getUseCase, searchUseCase, updateUseCase)

        val result = resource.search("casey", 1, 10)

        assertEquals(expectedResult, result)
        verify(exactly = 1) { searchUseCase.execute("casey", PageRequest(1, 10)) }
    }

    @Test
    fun searchesStudentsWithDefaultPageAndSizeWhenNotProvided() {
        val registerUseCase = mockk<RegisterStudentUseCase>()
        val getUseCase = mockk<GetStudentUseCase>()
        val searchUseCase = mockk<SearchStudentsUseCase>()
        val updateUseCase = mockk<UpdateStudentProfileUseCase>()
        val expectedResult =
            PageResult<StudentProfile>(
                items = emptyList(),
                page = 0,
                size = 20,
                totalElements = 0,
            )

        every { searchUseCase.execute(null, PageRequest(0, 20)) } returns expectedResult

        val resource = StudentResource(registerUseCase, getUseCase, searchUseCase, updateUseCase)

        val result = resource.search(null, null, null)

        assertEquals(expectedResult, result)
        verify(exactly = 1) { searchUseCase.execute(null, PageRequest(0, 20)) }
    }

    @Test
    fun updatesStudentAndReturnsUpdatedResponse() {
        val registerUseCase = mockk<RegisterStudentUseCase>()
        val getUseCase = mockk<GetStudentUseCase>()
        val searchUseCase = mockk<SearchStudentsUseCase>()
        val updateUseCase = mockk<UpdateStudentProfileUseCase>()
        val userId = UUID.fromString("00000000-0000-0000-0000-000000004004")
        val currentProfile =
            StudentProfile(
                userId = UserId(userId),
                firstName = "Dakota",
                lastName = "Lewis",
                phone = "555-4003",
                dateOfBirth = LocalDate.now().minusYears(20),
                guardianContact = null,
            )
        val updateRequest =
            RegisterStudentRequest(
                email = "old@example.com",
                password = "old",
                firstName = "Dakota",
                lastName = "Stone",
                phone = "555-4004",
                dateOfBirth = null,
                guardianContact = "new@example.com",
            )
        val updatedProfile =
            StudentProfile(
                userId = UserId(userId),
                firstName = "Dakota",
                lastName = "Stone",
                phone = "555-4004",
                dateOfBirth = LocalDate.now().minusYears(20),
                guardianContact = "new@example.com",
            )

        every { getUseCase.execute(UserId(userId)) } returns currentProfile
        every { updateUseCase.execute(any()) } returns updatedProfile

        val resource = StudentResource(registerUseCase, getUseCase, searchUseCase, updateUseCase)

        val response = resource.update(userId, updateRequest)

        assertEquals(userId, response.userId)
        assertEquals("Dakota", response.firstName)
        assertEquals("Stone", response.lastName)
        assertEquals("LEARNER", response.licenseStage)
        verify(exactly = 1) { getUseCase.execute(UserId(userId)) }
        verify(exactly = 1) { updateUseCase.execute(any()) }
    }

    @Test
    fun throwsWhenUpdatingNonExistentStudent() {
        val registerUseCase = mockk<RegisterStudentUseCase>()
        val getUseCase = mockk<GetStudentUseCase>()
        val searchUseCase = mockk<SearchStudentsUseCase>()
        val updateUseCase = mockk<UpdateStudentProfileUseCase>()
        val userId = UUID.fromString("00000000-0000-0000-0000-000000004005")
        val updateRequest =
            RegisterStudentRequest(
                email = "email@example.com",
                password = "password",
                firstName = "First",
                lastName = "Last",
                phone = null,
                dateOfBirth = null,
                guardianContact = null,
            )

        every { getUseCase.execute(UserId(userId)) } throws UserNotFoundException(UserId(userId))

        val resource = StudentResource(registerUseCase, getUseCase, searchUseCase, updateUseCase)

        assertThrows<UserNotFoundException> {
            resource.update(userId, updateRequest)
        }

        verify(exactly = 1) { getUseCase.execute(UserId(userId)) }
        verify(exactly = 0) { updateUseCase.execute(any()) }
    }
}


