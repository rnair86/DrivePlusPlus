package org.rnair.driveplusplus.appointmentservice.infrastructure.repository

import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.appointmentservice.core.models.Appointment
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentId
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentStatus
import java.time.Instant
import java.util.UUID

@QuarkusTest
class PanacheAppointmentRepositoryTest {
    @Test
    @TestTransaction
    fun overlapIgnoresCancelledRowsAndTheExcludedAppointment() {
        val repo = PanacheAppointmentRepository()
        val instructorId = UUID.randomUUID()
        val start = Instant.parse("2026-11-02T14:00:00Z")
        val end = Instant.parse("2026-11-02T15:00:00Z")
        val booked = repo.save(appointment(instructorId, start, end))
        repo.save(appointment(instructorId, start, end, AppointmentStatus.CANCELLED))

        assertTrue(repo.overlapsExisting(instructorId, Instant.parse("2026-11-02T14:30:00Z"), Instant.parse("2026-11-02T15:30:00Z")))
        assertFalse(repo.overlapsExisting(instructorId, start, end, booked.id))
    }

    @Test
    @TestTransaction
    fun listsAppointmentsByStudentInstructorAndStartWindow() {
        val repo = PanacheAppointmentRepository()
        val studentId = UUID.randomUUID()
        val instructorId = UUID.randomUUID()
        val matching =
            repo.save(
                appointment(
                    instructorId,
                    Instant.parse("2026-11-02T14:00:00Z"),
                    Instant.parse("2026-11-02T15:00:00Z"),
                    studentId = studentId,
                ),
            )
        repo.save(
            appointment(
                UUID.randomUUID(),
                Instant.parse("2026-12-01T14:00:00Z"),
                Instant.parse("2026-12-01T15:00:00Z"),
            ),
        )

        val listed =
            repo.list(
                studentId,
                instructorId,
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-30T00:00:00Z"),
            )

        assertEquals(listOf(matching.id), listed.map { it.id })
    }

    @Test
    @TestTransaction
    fun dueRemindersSkipSentCancelledAndFarFutureAppointments() {
        val repo = PanacheAppointmentRepository()
        val due =
            repo.save(
                appointment(
                    UUID.randomUUID(),
                    Instant.now().plusSeconds(3600),
                    Instant.now().plusSeconds(7200),
                ),
            )
        repo.save(
            appointment(
                UUID.randomUUID(),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
            ).copy(reminderSentAt = Instant.parse("2026-10-01T00:00:00Z")),
        )
        repo.save(
            appointment(
                UUID.randomUUID(),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                AppointmentStatus.CANCELLED,
            ),
        )
        repo.save(
            appointment(
                UUID.randomUUID(),
                Instant.now().plusSeconds(60 * 60 * 48),
                Instant.now().plusSeconds(60 * 60 * 49),
            ),
        )

        val dueIds = repo.findStartingWithinWithNoReminderSent(24).map { it.id }

        assertEquals(listOf(due.id), dueIds)
    }

    @Test
    @TestTransaction
    fun saveUpdatesAnExistingAppointment() {
        val repo = PanacheAppointmentRepository()
        val saved =
            repo.save(
                appointment(
                    UUID.randomUUID(),
                    Instant.parse("2026-11-02T14:00:00Z"),
                    Instant.parse("2026-11-02T15:00:00Z"),
                ),
            )
        val newStart = Instant.parse("2026-11-03T14:00:00Z")

        val updated = repo.save(saved.copy(startAt = newStart, status = AppointmentStatus.RESCHEDULED))

        assertEquals(newStart, repo.findByAppointmentId(saved.id)?.startAt)
        assertEquals(AppointmentStatus.RESCHEDULED, updated.status)
    }

    private fun appointment(
        instructorId: UUID,
        startAt: Instant,
        endAt: Instant,
        status: AppointmentStatus = AppointmentStatus.CONFIRMED,
        studentId: UUID = UUID.randomUUID(),
    ) = Appointment(
        id = AppointmentId(UUID.randomUUID()),
        studentId = studentId,
        instructorId = instructorId,
        vehicleId = null,
        startAt = startAt,
        endAt = endAt,
        pickupLocation = null,
        status = status,
    )
}
