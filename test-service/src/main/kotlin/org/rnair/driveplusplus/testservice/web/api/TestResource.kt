package org.rnair.driveplusplus.testservice.web.api

import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.testservice.application.usecase.CancelTestUseCase
import org.rnair.driveplusplus.testservice.application.usecase.ListTestsForStudentUseCase
import org.rnair.driveplusplus.testservice.application.usecase.RecordTestResultUseCase
import org.rnair.driveplusplus.testservice.application.usecase.RescheduleTestUseCase
import org.rnair.driveplusplus.testservice.application.usecase.ScheduleTestUseCase
import org.rnair.driveplusplus.testservice.core.models.TestSchedule
import org.rnair.driveplusplus.testservice.core.models.TestScheduleId
import org.rnair.driveplusplus.testservice.web.dtos.RecordTestResultRequest
import org.rnair.driveplusplus.testservice.web.dtos.RescheduleTestRequest
import org.rnair.driveplusplus.testservice.web.dtos.ScheduleTestRequest
import org.rnair.driveplusplus.testservice.web.dtos.TestScheduleResponse
import java.util.UUID

@Path("/api/v1/tests")
class TestResource(
    private val scheduleTestUseCase: ScheduleTestUseCase,
    private val listTestsForStudentUseCase: ListTestsForStudentUseCase,
    private val rescheduleTestUseCase: RescheduleTestUseCase,
    private val cancelTestUseCase: CancelTestUseCase,
    private val recordTestResultUseCase: RecordTestResultUseCase,
) {
    @POST
    @RunOnVirtualThread
    fun schedule(request: ScheduleTestRequest): Response {
        val test =
            scheduleTestUseCase.execute(
                request.studentId,
                request.testType,
                request.scheduledAt,
                request.location,
                request.examinerOrInstructorId,
            )
        return Response.status(201).entity(toResponse(test)).build()
    }

    @GET
    @RunOnVirtualThread
    fun list(
        @QueryParam("studentId") studentId: UUID,
    ): List<TestScheduleResponse> = listTestsForStudentUseCase.execute(studentId).map(::toResponse)

    @PUT
    @Path("/{id}")
    @RunOnVirtualThread
    fun reschedule(
        @PathParam("id") id: UUID,
        request: RescheduleTestRequest,
    ): TestScheduleResponse = toResponse(rescheduleTestUseCase.execute(TestScheduleId(id), request.scheduledAt))

    @DELETE
    @Path("/{id}")
    @RunOnVirtualThread
    fun cancel(
        @PathParam("id") id: UUID,
    ): Response {
        cancelTestUseCase.execute(TestScheduleId(id))
        return Response.noContent().build()
    }

    @POST
    @Path("/{id}/result")
    @RunOnVirtualThread
    fun recordResult(
        @PathParam("id") id: UUID,
        request: RecordTestResultRequest,
    ): Response {
        recordTestResultUseCase.execute(TestScheduleId(id), request.result, request.notes)
        return Response.noContent().build()
    }

    private fun toResponse(test: TestSchedule) =
        TestScheduleResponse(
            test.id.value,
            test.studentId,
            test.testType.name,
            test.scheduledAt,
            test.location,
            test.examinerOrInstructorId,
            test.status.name,
            test.result?.name,
            test.resultNotes,
            test.reminderSentAt,
        )
}
