package org.rnair.driveplusplus.appointmentservice.web.api

import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.appointmentservice.application.usecase.DefineAvailabilityUseCase
import org.rnair.driveplusplus.appointmentservice.application.usecase.GetAvailableSlotsUseCase
import org.rnair.driveplusplus.appointmentservice.core.models.AvailabilitySlot
import org.rnair.driveplusplus.appointmentservice.web.dtos.AvailabilityResponse
import org.rnair.driveplusplus.appointmentservice.web.dtos.DefineAvailabilityRequest
import java.util.UUID

@Path("/api/v1/availability")
class AvailabilityResource(
    private val defineAvailabilityUseCase: DefineAvailabilityUseCase,
    private val getAvailableSlotsUseCase: GetAvailableSlotsUseCase,
) {
    @POST
    @RunOnVirtualThread
    fun define(request: DefineAvailabilityRequest): Response {
        val slot =
            defineAvailabilityUseCase.execute(
                request.instructorId,
                request.dayOfWeek,
                request.startTime,
                request.endTime,
            )
        return Response.status(201).entity(toResponse(slot)).build()
    }

    @GET
    @RunOnVirtualThread
    fun list(
        @QueryParam("instructorId") instructorId: UUID,
    ): List<AvailabilityResponse> = getAvailableSlotsUseCase.execute(instructorId).map(::toResponse)

    private fun toResponse(slot: AvailabilitySlot) =
        AvailabilityResponse(
            slot.id.value,
            slot.instructorId,
            slot.dayOfWeek.name,
            slot.startTime,
            slot.endTime,
        )
}
