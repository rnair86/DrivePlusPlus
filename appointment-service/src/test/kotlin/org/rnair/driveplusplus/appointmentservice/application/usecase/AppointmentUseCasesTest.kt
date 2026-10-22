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
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.appointmentservice.core.exceptions.AppointmentNotFoundException
import org.rnair.driveplusplus.appointmentservice.core.exceptions.SlotConflictException
import org.rnair.driveplusplus.appointmentservice.core.models.Appointment
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentId
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentStatus
import org.rnair.driveplusplus.appointmentservice.core.repository.AppointmentRepository
import java.time.Instant
import java.util.UUID

class AppointmentUseCasesTest {
    @Test
    fun booksAppointmentAndPublishesCreatedEvent() {
        val studentId = UUID.fromString("00000000-0000-0000-0000-000000009101")
        val instructorId = UUID.fromString("00000000-0000-0000-0000-000000009102")
        val start = Instant.parse("2026-11-02T14:00:00Z")
        val end = Instant.parse("2026-11-02T15:00:00Z")
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<AppointmentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.overlapsExisting(instructorId, start, end, null) } returns false
        every { repo.save(any()) } answers { firstArg() }
        every { publisher.publish(capture(envelope)) } just runs

        val saved = BookAppointmentUseCase(repo, publisher).execute(studentId, instructorId, start, end, "School gate")

        assertEquals(studentId, saved.studentId)
        assertEquals(instructorId, saved.instructorId)
        assertEquals(null, saved.vehicleId)
        assertEquals(AppointmentStatus.CONFIRMED, saved.status)
        assertEquals("School gate", saved.pickupLocation)
        assertEquals("appointment.created", envelope.captured.eventType)
        val payload = envelope.captured.payload as AppointmentCreatedPayload
        assertEquals(saved.id.value, payload.appointmentId)
        assertEquals(studentId, payload.studentId)
        assertEquals(instructorId, payload.instructorId)
        assertEquals(start, payload.startAt)
    }

    @Test
    fun throwsWhenBookingOverlapsAndPublishesNothing() {
        val instructorId = UUID.randomUUID()
        val start = Instant.parse("2026-11-02T14:00:00Z")
        val end = Instant.parse("2026-11-02T15:00:00Z")
        val repo = mockk<AppointmentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.overlapsExisting(instructorId, start, end, null) } returns true

        assertThrows<SlotConflictException> {
            BookAppointmentUseCase(repo, publisher).execute(UUID.randomUUID(), instructorId, start, end, null)
        }
        verify(exactly = 0) { repo.save(any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun reschedulesAppointmentExcludingItselfAndPublishesEvent() {
        val existing = appointment()
        val newStart = Instant.parse("2026-11-03T14:00:00Z")
        val newEnd = Instant.parse("2026-11-03T15:00:00Z")
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<AppointmentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByAppointmentId(existing.id) } returns existing
        every { repo.overlapsExisting(existing.instructorId, newStart, newEnd, existing.id) } returns false
        every { repo.save(any()) } answers { firstArg() }
        every { publisher.publish(capture(envelope)) } just runs

        val updated = RescheduleAppointmentUseCase(repo, publisher).execute(existing.id, newStart, newEnd)

        assertEquals(newStart, updated.startAt)
        assertEquals(newEnd, updated.endAt)
        assertEquals(AppointmentStatus.RESCHEDULED, updated.status)
        assertEquals("appointment.rescheduled", envelope.captured.eventType)
        @Suppress("UNCHECKED_CAST")
        val payload = envelope.captured.payload as Map<String, Any>
        assertEquals(existing.id.value, payload["appointmentId"])
        assertEquals(existing.startAt, payload["oldStart"])
        assertEquals(newStart, payload["newStart"])
        verify(exactly = 1) { repo.overlapsExisting(existing.instructorId, newStart, newEnd, existing.id) }
    }

    @Test
    fun throwsWhenReschedulingOntoAnotherBooking() {
        val existing = appointment()
        val newStart = Instant.parse("2026-11-03T14:00:00Z")
        val newEnd = Instant.parse("2026-11-03T15:00:00Z")
        val repo = mockk<AppointmentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByAppointmentId(existing.id) } returns existing
        every { repo.overlapsExisting(existing.instructorId, newStart, newEnd, existing.id) } returns true

        assertThrows<SlotConflictException> {
            RescheduleAppointmentUseCase(repo, publisher).execute(existing.id, newStart, newEnd)
        }
        verify(exactly = 0) { repo.save(any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun throwsWhenReschedulingMissingAppointment() {
        val id = AppointmentId(UUID.randomUUID())
        val repo = mockk<AppointmentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByAppointmentId(id) } returns null

        assertThrows<AppointmentNotFoundException> {
            RescheduleAppointmentUseCase(
                repo,
                publisher,
            ).execute(id, Instant.parse("2026-11-03T14:00:00Z"), Instant.parse("2026-11-03T15:00:00Z"))
        }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun cancelsAppointmentAndPublishesEvent() {
        val existing = appointment()
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<AppointmentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByAppointmentId(existing.id) } returns existing
        every { repo.updateStatus(existing.id, AppointmentStatus.CANCELLED) } just runs
        every { publisher.publish(capture(envelope)) } just runs

        CancelAppointmentUseCase(repo, publisher).execute(existing.id)

        assertEquals("appointment.cancelled", envelope.captured.eventType)
        @Suppress("UNCHECKED_CAST")
        val payload = envelope.captured.payload as Map<String, UUID>
        assertEquals(existing.id.value, payload["appointmentId"])
        assertEquals(existing.studentId, payload["studentId"])
    }

    @Test
    fun throwsWhenCancellingMissingAppointment() {
        val id = AppointmentId(UUID.randomUUID())
        val repo = mockk<AppointmentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByAppointmentId(id) } returns null

        assertThrows<AppointmentNotFoundException> { CancelAppointmentUseCase(repo, publisher).execute(id) }
        verify(exactly = 0) { repo.updateStatus(any(), any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun completesAppointmentWithoutPublishing() {
        val id = AppointmentId(UUID.randomUUID())
        val repo = mockk<AppointmentRepository>()
        every { repo.updateStatus(id, AppointmentStatus.COMPLETED) } just runs

        CompleteAppointmentUseCase(repo).execute(id)

        verify(exactly = 1) { repo.updateStatus(id, AppointmentStatus.COMPLETED) }
    }

    @Test
    fun listsAppointmentsWithFilters() {
        val studentId = UUID.randomUUID()
        val from = Instant.parse("2026-11-01T00:00:00Z")
        val expected = listOf(appointment(studentId))
        val repo = mockk<AppointmentRepository>()
        every { repo.list(studentId, null, from, null) } returns expected

        assertEquals(expected, ListAppointmentsUseCase(repo).execute(studentId, null, from, null))
    }

    private fun appointment(studentId: UUID = UUID.fromString("00000000-0000-0000-0000-000000009201")) =
        Appointment(
            id = AppointmentId(UUID.fromString("00000000-0000-0000-0000-000000009210")),
            studentId = studentId,
            instructorId = UUID.fromString("00000000-0000-0000-0000-000000009202"),
            vehicleId = null,
            startAt = Instant.parse("2026-11-02T14:00:00Z"),
            endAt = Instant.parse("2026-11-02T15:00:00Z"),
            pickupLocation = "School gate",
        )
}
