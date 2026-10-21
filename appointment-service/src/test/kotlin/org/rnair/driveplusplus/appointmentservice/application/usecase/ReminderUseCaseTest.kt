package org.rnair.driveplusplus.appointmentservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.appointmentservice.core.models.Appointment
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentId
import org.rnair.driveplusplus.appointmentservice.core.repository.AppointmentRepository
import java.time.Instant
import java.util.UUID

class ReminderUseCaseTest {
    @Test
    fun publishesReminderAndMarksItSent() {
        val appointment =
            Appointment(
                id = AppointmentId(UUID.fromString("00000000-0000-0000-0000-000000009301")),
                studentId = UUID.fromString("00000000-0000-0000-0000-000000009302"),
                instructorId = UUID.fromString("00000000-0000-0000-0000-000000009303"),
                vehicleId = null,
                startAt = Instant.parse("2026-11-02T14:00:00Z"),
                endAt = Instant.parse("2026-11-02T15:00:00Z"),
                pickupLocation = "School gate",
            )
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<AppointmentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findStartingWithinWithNoReminderSent(24) } returns listOf(appointment)
        every { publisher.publish(capture(envelope)) } just runs
        every { repo.markReminderSent(appointment.id) } just runs

        FindDueReminderAppointmentsUseCase(repo, publisher).execute()

        assertEquals("appointment.reminder.due", envelope.captured.eventType)
        val payload = envelope.captured.payload as AppointmentReminderDuePayload
        assertEquals(appointment.id.value, payload.appointmentId)
        assertEquals(appointment.studentId, payload.studentId)
        assertEquals(appointment.instructorId, payload.instructorId)
        assertEquals(appointment.startAt, payload.startAt)
        assertEquals("School gate", payload.pickupLocation)
        verify(exactly = 1) { repo.markReminderSent(appointment.id) }
    }
}
