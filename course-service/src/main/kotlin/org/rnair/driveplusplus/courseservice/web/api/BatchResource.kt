package org.rnair.driveplusplus.courseservice.web.api

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.courseservice.application.usecase.CancelSessionUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.CreateClassBatchUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.ListBatchesUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.ListSessionsForBatchUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.RescheduleSessionUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.ScheduleSessionUseCase
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.BatchStatus
import org.rnair.driveplusplus.courseservice.core.models.ClassBatch
import org.rnair.driveplusplus.courseservice.core.models.ClassSession
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.SessionId
import org.rnair.driveplusplus.courseservice.web.dtos.BatchResponse
import org.rnair.driveplusplus.courseservice.web.dtos.CreateBatchRequest
import org.rnair.driveplusplus.courseservice.web.dtos.RescheduleSessionRequest
import org.rnair.driveplusplus.courseservice.web.dtos.ScheduleSessionRequest
import org.rnair.driveplusplus.courseservice.web.dtos.SessionResponse
import java.util.UUID

@Path("/api/v1/batches")
class BatchResource(
    private val createClassBatchUseCase: CreateClassBatchUseCase,
    private val listBatchesUseCase: ListBatchesUseCase,
    private val listSessionsForBatchUseCase: ListSessionsForBatchUseCase,
    private val scheduleSessionUseCase: ScheduleSessionUseCase,
    private val rescheduleSessionUseCase: RescheduleSessionUseCase,
    private val cancelSessionUseCase: CancelSessionUseCase,
) {
    @POST
    @RunOnVirtualThread
    fun create(request: CreateBatchRequest): Response {
        val batch =
            createClassBatchUseCase.execute(
                CourseId(request.courseId),
                request.instructorId,
                request.startDate,
                request.endDate,
                request.capacity,
            )
        return Response.status(201).entity(toResponse(batch)).build()
    }

    @GET
    @RunOnVirtualThread
    fun list(
        @QueryParam("courseId") courseId: UUID?,
        @QueryParam("instructorId") instructorId: UUID?,
        @QueryParam("status") status: BatchStatus?,
        @QueryParam("page") page: Int?,
        @QueryParam("size") size: Int?,
    ): PageResult<BatchResponse> {
        val result =
            listBatchesUseCase.execute(
                courseId?.let(::CourseId),
                instructorId,
                status,
                PageRequest(page ?: 0, size ?: 20),
            )
        return PageResult(result.items.map(::toResponse), result.page, result.size, result.totalElements)
    }

    @GET
    @Path("/{id}/sessions")
    @RunOnVirtualThread
    fun listSessions(
        @PathParam("id") id: UUID,
    ): List<SessionResponse> = listSessionsForBatchUseCase.execute(BatchId(id)).map(::toSession)

    @POST
    @Path("/{id}/sessions")
    @RunOnVirtualThread
    fun schedule(
        @PathParam("id") id: UUID,
        request: ScheduleSessionRequest,
    ): Response {
        val session =
            scheduleSessionUseCase.execute(
                BatchId(id),
                request.sessionDate,
                request.startTime,
                request.endTime,
                request.location,
            )
        return Response.status(201).entity(toSession(session)).build()
    }

    @PUT
    @Path("/{id}/sessions/{sessionId}")
    @RunOnVirtualThread
    fun reschedule(
        @PathParam("id") id: UUID,
        @PathParam("sessionId") sessionId: UUID,
        request: RescheduleSessionRequest,
    ): SessionResponse {
        val session =
            rescheduleSessionUseCase.execute(
                SessionId(sessionId),
                request.sessionDate,
                request.startTime,
                request.endTime,
            )
        return toSession(session)
    }

    @DELETE
    @Path("/{id}/sessions/{sessionId}")
    @RunOnVirtualThread
    fun cancel(
        @PathParam("id") id: UUID,
        @PathParam("sessionId") sessionId: UUID,
    ): Response {
        cancelSessionUseCase.execute(SessionId(sessionId))
        return Response.noContent().build()
    }

    private fun toResponse(batch: ClassBatch) =
        BatchResponse(
            batch.id.value,
            batch.courseId.value,
            batch.instructorId,
            batch.startDate,
            batch.endDate,
            batch.capacity,
            batch.status.name,
        )

    private fun toSession(session: ClassSession) =
        SessionResponse(
            session.id.value,
            session.batchId.value,
            session.sessionDate,
            session.startTime,
            session.endTime,
            session.location,
            session.status.name,
        )
}
