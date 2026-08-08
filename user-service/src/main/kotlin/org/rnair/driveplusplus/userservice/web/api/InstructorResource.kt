package org.rnair.driveplusplus.userservice.web.api

import com.rnair.drivingplusplus.commons.paging.PageRequest
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.userservice.application.usecase.GetInstructorUseCase
import org.rnair.driveplusplus.userservice.application.usecase.ListInstructorsUseCase
import org.rnair.driveplusplus.userservice.application.usecase.RegisterInstructorCommand
import org.rnair.driveplusplus.userservice.application.usecase.RegisterInstructorUseCase
import org.rnair.driveplusplus.userservice.application.usecase.UpdateInstructorProfileUseCase
import org.rnair.driveplusplus.userservice.core.model.InstructorProfile
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.web.dtos.InstructorResponse
import org.rnair.driveplusplus.userservice.web.dtos.RegisterInstructorRequest
import java.util.UUID

@Path("/api/v1/instructors")
class InstructorResource(
    private val registerInstructorUseCase: RegisterInstructorUseCase,
    private val getInstructorUseCase: GetInstructorUseCase,
    private val listInstructorsUseCase: ListInstructorsUseCase,
    private val updateInstructorProfileUseCase: UpdateInstructorProfileUseCase,
) {
    @POST
    @RunOnVirtualThread
    fun register(request: RegisterInstructorRequest): Response {
        val p =
            registerInstructorUseCase.execute(
                RegisterInstructorCommand(
                    request.email,
                    request.password,
                    request.firstName,
                    request.lastName,
                    request.phone,
                    request.licenseCertNumber,
                    request.yearsExperience,
                    request.specialties,
                    request.vehicleAssigned,
                ),
            )
        return Response.status(201).entity(toResponse(p)).build()
    }

    @GET
    @Path("/{id}")
    @RunOnVirtualThread
    fun get(
        @PathParam("id") id: UUID,
    ) = toResponse(getInstructorUseCase.execute(UserId(id)))

    @GET @RunOnVirtualThread
    fun list(
        @QueryParam("page") page: Int?,
        @QueryParam("size") size: Int?,
    ) = listInstructorsUseCase.execute(PageRequest(page ?: 0, size ?: 20))

    @PUT
    @Path("/{id}")
    @RunOnVirtualThread
    fun update(
        @PathParam("id") id: UUID,
        request: RegisterInstructorRequest,
    ): InstructorResponse {
        val current = getInstructorUseCase.execute(UserId(id))
        return toResponse(
            updateInstructorProfileUseCase.execute(
                current.copy(
                    firstName = request.firstName,
                    lastName = request.lastName,
                    phone = request.phone,
                    specialties = request.specialties,
                    vehicleAssigned = request.vehicleAssigned,
                ),
            ),
        )
    }

    private fun toResponse(p: InstructorProfile) = InstructorResponse(p.userId.value, p.firstName, p.lastName, p.specialties)
}
