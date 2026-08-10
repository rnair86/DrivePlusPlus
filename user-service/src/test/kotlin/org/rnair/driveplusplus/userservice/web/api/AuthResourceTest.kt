package org.rnair.driveplusplus.userservice.web.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.userservice.application.usecase.AuthenticateUserUseCase
import org.rnair.driveplusplus.userservice.core.exceptions.InvalidCredentialsException
import org.rnair.driveplusplus.userservice.web.dtos.LoginRequest

class AuthResourceTest {
    @Test
    fun returnsTokenWrappedInLoginResponseWhenCredentialsAreValid() {
        val useCase = mockk<AuthenticateUserUseCase>()

        every { useCase.execute("user@example.com", "correct-password") } returns "jwt-token"

        val resource = AuthResource(useCase)

        val response = resource.login(LoginRequest("user@example.com", "correct-password"))

        assertEquals("jwt-token", response.token)
        verify(exactly = 1) { useCase.execute("user@example.com", "correct-password") }
    }

    @Test
    fun forwardsEmailAndPasswordFromRequestToUseCase() {
        val useCase = mockk<AuthenticateUserUseCase>()

        every { useCase.execute("admin@example.com", "secret") } returns "admin-jwt"

        val resource = AuthResource(useCase)

        val response = resource.login(LoginRequest("admin@example.com", "secret"))

        assertEquals("admin-jwt", response.token)
        verify(exactly = 1) { useCase.execute("admin@example.com", "secret") }
    }

    @Test
    fun propagatesInvalidCredentialsExceptionWhenUseCaseThrows() {
        val useCase = mockk<AuthenticateUserUseCase>()

        every { useCase.execute("user@example.com", "wrong-password") } throws InvalidCredentialsException()

        val resource = AuthResource(useCase)

        assertThrows<InvalidCredentialsException> {
            resource.login(LoginRequest("user@example.com", "wrong-password"))
        }

        verify(exactly = 1) { useCase.execute("user@example.com", "wrong-password") }
    }
}

