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
import org.rnair.driveplusplus.userservice.application.usecase.GetStudentUseCase
import org.rnair.driveplusplus.userservice.application.usecase.RegisterStudentCommand
import org.rnair.driveplusplus.userservice.application.usecase.RegisterStudentUseCase
import org.rnair.driveplusplus.userservice.application.usecase.SearchStudentsUseCase
import org.rnair.driveplusplus.userservice.application.usecase.UpdateStudentProfileUseCase
import org.rnair.driveplusplus.userservice.core.model.StudentProfile
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.web.dtos.RegisterStudentRequest
import org.rnair.driveplusplus.userservice.web.dtos.StudentResponse
import java.util.UUID

@Path("/api/v1/students")
class StudentResource(
    private val registerStudentUseCase: RegisterStudentUseCase,
    private val getStudentUseCase: GetStudentUseCase,
    private val searchStudentsUseCase: SearchStudentsUseCase,
    private val updateStudentProfileUseCase: UpdateStudentProfileUseCase,
) {
    @POST
    @RunOnVirtualThread
    fun register(request: RegisterStudentRequest): Response {
        val p =
            registerStudentUseCase.execute(
                RegisterStudentCommand(
                    request.email,
                    request.password,
                    request.firstName,
                    request.lastName,
                    request.phone,
                    request.dateOfBirth,
                    request.guardianContact,
                ),
            )
        return Response.status(201).entity(toResponse(p)).build()
    }

    @GET
    @Path("/{id}")
    @RunOnVirtualThread
    fun get(
        @PathParam("id") id: UUID,
    ): StudentResponse = toResponse(getStudentUseCase.execute(UserId(id)))

    @GET @RunOnVirtualThread
    fun search(
        @QueryParam("q") q: String?,
        @QueryParam("page") page: Int?,
        @QueryParam("size") size: Int?,
    ) = searchStudentsUseCase.execute(q, PageRequest(page ?: 0, size ?: 20))

    @PUT
    @Path("/{id}")
    @RunOnVirtualThread
    fun update(
        @PathParam("id") id: UUID,
        request: RegisterStudentRequest,
    ): StudentResponse {
        val current = getStudentUseCase.execute(UserId(id))
        return toResponse(
            updateStudentProfileUseCase.execute(
                current.copy(
                    firstName = request.firstName,
                    lastName = request.lastName,
                    phone = request.phone,
                    guardianContact = request.guardianContact,
                ),
            ),
        )
    }

    private fun toResponse(p: StudentProfile) = StudentResponse(p.userId.value, "", p.firstName, p.lastName, p.licenseStage.name)
}
