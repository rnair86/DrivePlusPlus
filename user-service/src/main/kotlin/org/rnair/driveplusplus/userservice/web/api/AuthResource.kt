package org.rnair.driveplusplus.userservice.web.api

import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import org.rnair.driveplusplus.userservice.application.usecase.AuthenticateUserUseCase
import org.rnair.driveplusplus.userservice.web.dtos.LoginRequest
import org.rnair.driveplusplus.userservice.web.dtos.LoginResponse

@Path("/api/v1/auth")
class AuthResource(
    private val authenticateUserUseCase: AuthenticateUserUseCase,
) {
    @POST
    @Path("/login")
    @RunOnVirtualThread
    fun login(request: LoginRequest): LoginResponse = LoginResponse(authenticateUserUseCase.execute(request.email, request.password))
}
