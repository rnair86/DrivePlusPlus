package org.rnair.driveplusplus.appointmentservice.web.api

import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.appointmentservice.application.usecase.BookAppointmentUseCase
import org.rnair.driveplusplus.appointmentservice.application.usecase.CancelAppointmentUseCase
import org.rnair.driveplusplus.appointmentservice.application.usecase.CompleteAppointmentUseCase
import org.rnair.driveplusplus.appointmentservice.application.usecase.ListAppointmentsUseCase
import org.rnair.driveplusplus.appointmentservice.application.usecase.RescheduleAppointmentUseCase
import org.rnair.driveplusplus.appointmentservice.core.models.Appointment
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentId
import org.rnair.driveplusplus.appointmentservice.web.dtos.AppointmentResponse
import org.rnair.driveplusplus.appointmentservice.web.dtos.BookAppointmentRequest
import org.rnair.driveplusplus.appointmentservice.web.dtos.RescheduleAppointmentRequest
import java.time.Instant
import java.util.UUID

@Path("/api/v1/appointments")
class AppointmentResource(
    private val bookAppointmentUseCase: BookAppointmentUseCase,
    private val listAppointmentsUseCase: ListAppointmentsUseCase,
    private val rescheduleAppointmentUseCase: RescheduleAppointmentUseCase,
    private val cancelAppointmentUseCase: CancelAppointmentUseCase,
    private val completeAppointmentUseCase: CompleteAppointmentUseCase,
) {
    @POST
    @RunOnVirtualThread
    fun book(request: BookAppointmentRequest): Response {
        val appointment =
            bookAppointmentUseCase.execute(
                request.studentId,
                request.instructorId,
                request.startAt,
                request.endAt,
                request.pickupLocation,
            )
        return Response.status(201).entity(toResponse(appointment)).build()
    }

    @GET
    @RunOnVirtualThread
    fun list(
        @QueryParam("studentId") studentId: UUID?,
        @QueryParam("instructorId") instructorId: UUID?,
        @QueryParam("from") from: Instant?,
        @QueryParam("to") to: Instant?,
    ): List<AppointmentResponse> = listAppointmentsUseCase.execute(studentId, instructorId, from, to).map(::toResponse)

    @PUT
    @Path("/{id}")
    @RunOnVirtualThread
    fun reschedule(
        @PathParam("id") id: UUID,
        request: RescheduleAppointmentRequest,
    ): AppointmentResponse = toResponse(rescheduleAppointmentUseCase.execute(AppointmentId(id), request.startAt, request.endAt))

    @DELETE
    @Path("/{id}")
    @RunOnVirtualThread
    fun cancel(
        @PathParam("id") id: UUID,
    ): Response {
        cancelAppointmentUseCase.execute(AppointmentId(id))
        return Response.noContent().build()
    }

    @POST
    @Path("/{id}/complete")
    @RunOnVirtualThread
    fun complete(
        @PathParam("id") id: UUID,
    ): Response {
        completeAppointmentUseCase.execute(AppointmentId(id))
        return Response.noContent().build()
    }

    private fun toResponse(appointment: Appointment) =
        AppointmentResponse(
            appointment.id.value,
            appointment.studentId,
            appointment.instructorId,
            appointment.vehicleId,
            appointment.startAt,
            appointment.endAt,
            appointment.pickupLocation,
            appointment.status.name,
            appointment.notes,
            appointment.reminderSentAt,
        )
}
