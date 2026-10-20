package org.rnair.driveplusplus.userservice.web.api

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.userservice.application.usecase.UpdateAccountStatusUseCase
import org.rnair.driveplusplus.userservice.core.exceptions.UserNotFoundException
import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.web.dtos.UpdateStatusRequest
import java.util.UUID

class UserResourceTest {
    @Test
    fun returnsNoContentWhenStatusIsUpdatedSuccessfully() {
        val userId = UUID.fromString("00000000-0000-0000-0000-000000003001")
        val useCase = mockk<UpdateAccountStatusUseCase>()

        every { useCase.execute(UserId(userId), AccountStatus.SUSPENDED) } just runs

        val resource = UserResource(useCase)

        val response = resource.updateStatus(userId, UpdateStatusRequest(AccountStatus.SUSPENDED))

        assertEquals(204, response.status)
        verify(exactly = 1) { useCase.execute(UserId(userId), AccountStatus.SUSPENDED) }
    }

    @Test
    fun forwardsUserIdAndStatusFromRequestToUseCase() {
        val userId = UUID.fromString("00000000-0000-0000-0000-000000003002")
        val useCase = mockk<UpdateAccountStatusUseCase>()

        every { useCase.execute(UserId(userId), AccountStatus.ARCHIVED) } just runs

        val resource = UserResource(useCase)

        resource.updateStatus(userId, UpdateStatusRequest(AccountStatus.ARCHIVED))

        verify(exactly = 1) { useCase.execute(UserId(userId), AccountStatus.ARCHIVED) }
    }

    @Test
    fun updatesToActiveStatus() {
        val userId = UUID.fromString("00000000-0000-0000-0000-000000003003")
        val useCase = mockk<UpdateAccountStatusUseCase>()

        every { useCase.execute(UserId(userId), AccountStatus.ACTIVE) } just runs

        val resource = UserResource(useCase)

        val response = resource.updateStatus(userId, UpdateStatusRequest(AccountStatus.ACTIVE))

        assertEquals(204, response.status)
        verify(exactly = 1) { useCase.execute(UserId(userId), AccountStatus.ACTIVE) }
    }

    @Test
    fun propagatesUserNotFoundExceptionWhenUseCaseThrows() {
        val userId = UUID.fromString("00000000-0000-0000-0000-000000003004")
        val useCase = mockk<UpdateAccountStatusUseCase>()

        every { useCase.execute(UserId(userId), AccountStatus.SUSPENDED) } throws UserNotFoundException(UserId(userId))

        val resource = UserResource(useCase)

        assertThrows<UserNotFoundException> {
            resource.updateStatus(userId, UpdateStatusRequest(AccountStatus.SUSPENDED))
        }

        verify(exactly = 1) { useCase.execute(UserId(userId), AccountStatus.SUSPENDED) }
    }
}
