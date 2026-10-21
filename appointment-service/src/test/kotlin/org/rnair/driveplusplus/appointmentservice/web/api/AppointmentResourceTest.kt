package org.rnair.driveplusplus.appointmentservice.web.api

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.appointmentservice.application.usecase.BookAppointmentUseCase
import org.rnair.driveplusplus.appointmentservice.application.usecase.CancelAppointmentUseCase
import org.rnair.driveplusplus.appointmentservice.application.usecase.CompleteAppointmentUseCase
import org.rnair.driveplusplus.appointmentservice.application.usecase.ListAppointmentsUseCase
import org.rnair.driveplusplus.appointmentservice.application.usecase.RescheduleAppointmentUseCase
import org.rnair.driveplusplus.appointmentservice.core.exceptions.SlotConflictException
import org.rnair.driveplusplus.appointmentservice.core.models.Appointment
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentId
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentStatus
import org.rnair.driveplusplus.appointmentservice.web.dtos.BookAppointmentRequest
import org.rnair.driveplusplus.appointmentservice.web.dtos.RescheduleAppointmentRequest
import java.time.Instant
import java.util.UUID

class AppointmentResourceTest {
    @Test
    fun booksAppointmentAndReturnsCreatedStatus() {
        val book = mockk<BookAppointmentUseCase>()
        val studentId = UUID.fromString("00000000-0000-0000-0000-000000009501")
        val instructorId = UUID.fromString("00000000-0000-0000-0000-000000009502")
        val start = Instant.parse("2026-11-02T14:00:00Z")
        val end = Instant.parse("2026-11-02T15:00:00Z")
        every { book.execute(any(), any(), any(), any(), any()) } returns appointment(studentId, instructorId, start, end)

        val response = resource(book = book).book(BookAppointmentRequest(studentId, instructorId, start, end, "School gate"))

        assertEquals(201, response.status)
        verify(exactly = 1) { book.execute(studentId, instructorId, start, end, "School gate") }
    }

    @Test
    fun propagatesSlotConflict() {
        val book = mockk<BookAppointmentUseCase>()
        val instructorId = UUID.randomUUID()
        every { book.execute(any(), any(), any(), any(), any()) } throws SlotConflictException(instructorId)

        assertThrows<SlotConflictException> {
            resource(book = book).book(
                BookAppointmentRequest(
                    UUID.randomUUID(),
                    instructorId,
                    Instant.parse("2026-11-02T14:00:00Z"),
                    Instant.parse("2026-11-02T15:00:00Z"),
                    null,
                ),
            )
        }
    }

    @Test
    fun listsAppointmentsWithFilters() {
        val list = mockk<ListAppointmentsUseCase>()
        val studentId = UUID.randomUUID()
        val from = Instant.parse("2026-11-01T00:00:00Z")
        every { list.execute(studentId, null, from, null) } returns emptyList()

        val result = resource(list = list).list(studentId, null, from, null)

        assertEquals(emptyList<Any>(), result)
        verify(exactly = 1) { list.execute(studentId, null, from, null) }
    }

    @Test
    fun reschedulesAppointment() {
        val reschedule = mockk<RescheduleAppointmentUseCase>()
        val id = UUID.fromString("00000000-0000-0000-0000-000000009510")
        val start = Instant.parse("2026-11-03T14:00:00Z")
        val end = Instant.parse("2026-11-03T15:00:00Z")
        every { reschedule.execute(any(), any(), any()) } returns
            appointment(UUID.randomUUID(), UUID.randomUUID(), start, end, AppointmentId(id), AppointmentStatus.RESCHEDULED)

        val response = resource(reschedule = reschedule).reschedule(id, RescheduleAppointmentRequest(start, end))

        assertEquals(start, response.startAt)
        assertEquals("RESCHEDULED", response.status)
        verify(exactly = 1) { reschedule.execute(AppointmentId(id), start, end) }
    }

    @Test
    fun cancelsAppointmentAndReturnsNoContent() {
        val cancel = mockk<CancelAppointmentUseCase>()
        val id = UUID.randomUUID()
        every { cancel.execute(AppointmentId(id)) } just runs

        val response = resource(cancel = cancel).cancel(id)

        assertEquals(204, response.status)
    }

    @Test
    fun completesAppointmentAndReturnsNoContent() {
        val complete = mockk<CompleteAppointmentUseCase>()
        val id = UUID.randomUUID()
        every { complete.execute(AppointmentId(id)) } just runs

        val response = resource(complete = complete).complete(id)

        assertEquals(204, response.status)
        verify(exactly = 1) { complete.execute(AppointmentId(id)) }
    }

    private fun resource(
        book: BookAppointmentUseCase = mockk(),
        list: ListAppointmentsUseCase = mockk(),
        reschedule: RescheduleAppointmentUseCase = mockk(),
        cancel: CancelAppointmentUseCase = mockk(),
        complete: CompleteAppointmentUseCase = mockk(),
    ) = AppointmentResource(book, list, reschedule, cancel, complete)

    private fun appointment(
        studentId: UUID,
        instructorId: UUID,
        start: Instant,
        end: Instant,
        id: AppointmentId = AppointmentId(UUID.fromString("00000000-0000-0000-0000-000000009510")),
        status: AppointmentStatus = AppointmentStatus.CONFIRMED,
    ) = Appointment(id, studentId, instructorId, null, start, end, "School gate", status)
}
