package org.rnair.driveplusplus.courseservice.web.api

import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.courseservice.application.usecase.EnrollStudentUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.ListEnrollmentsForStudentUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.WithdrawEnrollmentUseCase
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.Enrollment
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentId
import org.rnair.driveplusplus.courseservice.web.dtos.EnrollRequest
import org.rnair.driveplusplus.courseservice.web.dtos.EnrollmentResponse
import java.util.UUID

@Path("/api/v1/enrollments")
class EnrollmentResource(
    private val enrollStudentUseCase: EnrollStudentUseCase,
    private val listEnrollmentsForStudentUseCase: ListEnrollmentsForStudentUseCase,
    private val withdrawEnrollmentUseCase: WithdrawEnrollmentUseCase,
) {
    @POST
    @RunOnVirtualThread
    fun enroll(request: EnrollRequest): Response {
        val enrollment = enrollStudentUseCase.execute(request.studentId, BatchId(request.batchId))
        return Response.status(201).entity(toResponse(enrollment)).build()
    }

    @GET
    @RunOnVirtualThread
    fun list(
        @QueryParam("studentId") studentId: UUID,
    ): List<EnrollmentResponse> = listEnrollmentsForStudentUseCase.execute(studentId).map(::toResponse)

    @DELETE
    @Path("/{id}")
    @RunOnVirtualThread
    fun withdraw(
        @PathParam("id") id: UUID,
    ): Response {
        withdrawEnrollmentUseCase.execute(EnrollmentId(id))
        return Response.noContent().build()
    }

    private fun toResponse(enrollment: Enrollment) =
        EnrollmentResponse(
            enrollment.id.value,
            enrollment.studentId,
            enrollment.batchId.value,
            enrollment.enrolledAt,
            enrollment.status.name,
        )
}
