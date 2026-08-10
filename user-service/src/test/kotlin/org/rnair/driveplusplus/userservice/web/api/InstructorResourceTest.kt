package org.rnair.driveplusplus.userservice.web.api
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.userservice.application.usecase.GetInstructorUseCase
import org.rnair.driveplusplus.userservice.application.usecase.RegisterInstructorUseCase
import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import org.rnair.driveplusplus.userservice.application.usecase.ListInstructorsUseCase
import org.rnair.driveplusplus.userservice.application.usecase.UpdateInstructorProfileUseCase
import org.rnair.driveplusplus.userservice.core.exceptions.UserNotFoundException
import org.rnair.driveplusplus.userservice.core.model.InstructorProfile
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.web.dtos.RegisterInstructorRequest
class InstructorResourceTest {
    @Test
    fun registersInstructorAndReturnsCreatedStatusWithResponse() {
        val registerUseCase = mockk<RegisterInstructorUseCase>()
        val getUseCase = mockk<GetInstructorUseCase>()
        val profile =
            InstructorProfile(
                userId = UserId(UUID.fromString("00000000-0000-0000-0000-000000005001")),
                firstName = "Riley",
                lastName = "Foster",
                phone = "555-5001",
                licenseCertNumber = "CERT-5001",
                yearsExperience = 8,
                specialties = listOf("highway driving"),
                vehicleAssigned = "CAR-200",
            )
        every { registerUseCase.execute(any()) } returns profile
        val resource = InstructorResource(registerUseCase, getUseCase, mockk(), mockk())
        val response = resource.register(RegisterInstructorRequest("instructor@example.com", "password", "Riley", "Foster", "555-5001", "CERT-5001", 8, listOf("highway driving"), "CAR-200"))
        assertEquals(201, response.status)
    }
    @Test
    fun returnsInstructorProfileWhenGettingById() {
        val userId = UUID.fromString("00000000-0000-0000-0000-000000005002")
        val getUseCase = mockk<GetInstructorUseCase>()
        val profile =
            InstructorProfile(
                userId = UserId(userId),
                firstName = "Taylor",
                lastName = "Palmer",
                phone = "555-5002",
                licenseCertNumber = "CERT-5002",
                yearsExperience = 12,
                specialties = listOf("city driving"),
                vehicleAssigned = "CAR-201",
            )
        every { getUseCase.execute(UserId(userId)) } returns profile
        val resource = InstructorResource(mockk(), getUseCase, mockk(), mockk())
        val response = resource.get(userId)
        assertEquals(userId, response.userId)
        assertEquals("Taylor", response.firstName)
    }
    @Test
    fun throwsWhenGettingNonExistentInstructor() {
        val userId = UUID.fromString("00000000-0000-0000-0000-000000005003")
        val getUseCase = mockk<GetInstructorUseCase>()
        every { getUseCase.execute(UserId(userId)) } throws UserNotFoundException(UserId(userId))
        val resource = InstructorResource(mockk(), getUseCase, mockk(), mockk())
        assertThrows<UserNotFoundException> {
            resource.get(userId)
        }
    }

    @Test
    fun listsInstructorsWithPaginationParameters() {
        val listUseCase = mockk<ListInstructorsUseCase>()
        val result = PageResult<InstructorProfile>(items = emptyList(), page = 2, size = 15, totalElements = 0)
        every { listUseCase.execute(PageRequest(2, 15)) } returns result
        val resource = InstructorResource(mockk(), mockk(), listUseCase, mockk())
        val response = resource.list(2, 15)
        assertEquals(result, response)
        verify(exactly = 1) { listUseCase.execute(PageRequest(2, 15)) }
    }

    @Test
    fun listsInstructorsWithDefaultPageAndSizeWhenNotProvided() {
        val listUseCase = mockk<ListInstructorsUseCase>()
        val result = PageResult<InstructorProfile>(items = emptyList(), page = 0, size = 20, totalElements = 0)
        every { listUseCase.execute(PageRequest(0, 20)) } returns result
        val resource = InstructorResource(mockk(), mockk(), listUseCase, mockk())
        val response = resource.list(null, null)
        assertEquals(result, response)
        verify(exactly = 1) { listUseCase.execute(PageRequest(0, 20)) }
    }

    @Test
    fun updatesInstructorAndReturnsUpdatedResponse() {
        val userId = UUID.fromString("00000000-0000-0000-0000-000000005004")
        val getUseCase = mockk<GetInstructorUseCase>()
        val updateUseCase = mockk<UpdateInstructorProfileUseCase>()
        val current = InstructorProfile(UserId(userId), "Jordan", "Bennett", "555-5003", "CERT-5003", 5, listOf("basic"), "CAR-202")
        val updated = InstructorProfile(UserId(userId), "Jordan", "Brooks", "555-5004", "CERT-5003", 5, listOf("advanced"), "CAR-203")
        val request = RegisterInstructorRequest("old@example.com", "old", "Jordan", "Brooks", "555-5004", null, 0, listOf("advanced"), "CAR-203")
        every { getUseCase.execute(UserId(userId)) } returns current
        every { updateUseCase.execute(any()) } returns updated
        val resource = InstructorResource(mockk(), getUseCase, mockk(), updateUseCase)
        val response = resource.update(userId, request)
        assertEquals(userId, response.userId)
        assertEquals("Jordan", response.firstName)
        assertEquals("Brooks", response.lastName)
        verify(exactly = 1) { getUseCase.execute(UserId(userId)) }
        verify(exactly = 1) { updateUseCase.execute(any()) }
    }

    @Test
    fun throwsWhenUpdatingNonExistentInstructor() {
        val userId = UUID.fromString("00000000-0000-0000-0000-000000005005")
        val getUseCase = mockk<GetInstructorUseCase>()
        val updateUseCase = mockk<UpdateInstructorProfileUseCase>()
        val request = RegisterInstructorRequest("email@example.com", "password", "First", "Last", null, null, 0, emptyList(), null)
        every { getUseCase.execute(UserId(userId)) } throws UserNotFoundException(UserId(userId))
        val resource = InstructorResource(mockk(), getUseCase, mockk(), updateUseCase)
        assertThrows<UserNotFoundException> {
            resource.update(userId, request)
        }
        verify(exactly = 0) { updateUseCase.execute(any()) }
    }
}
