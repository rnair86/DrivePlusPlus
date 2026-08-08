package org.rnair.driveplusplus.userservice.web.api

import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.userservice.application.usecase.UpdateAccountStatusUseCase
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.web.dtos.UpdateStatusRequest
import java.util.UUID

@Path("/api/v1/users")
class UserResource(
    private val updateAccountStatusUseCase: UpdateAccountStatusUseCase,
) {
    @PATCH
    @Path("/{id}/status")
    @RunOnVirtualThread
    fun updateStatus(
        @PathParam("id") id: UUID,
        request: UpdateStatusRequest,
    ): Response {
        updateAccountStatusUseCase.execute(UserId(id), request.status)
        return Response.noContent().build()
    }
}
