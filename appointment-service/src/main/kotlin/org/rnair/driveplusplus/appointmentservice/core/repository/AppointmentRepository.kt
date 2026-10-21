package org.rnair.driveplusplus.appointmentservice.core.repository

import org.rnair.driveplusplus.appointmentservice.core.models.Appointment
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentId
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentStatus
import java.time.Instant
import java.util.UUID

interface AppointmentRepository {
    fun save(appointment: Appointment): Appointment

    fun findByAppointmentId(id: AppointmentId): Appointment?

    fun overlapsExisting(
        instructorId: UUID,
        startAt: Instant,
        endAt: Instant,
        excludeId: AppointmentId? = null,
    ): Boolean

    fun list(
        studentId: UUID?,
        instructorId: UUID?,
        from: Instant?,
        to: Instant?,
    ): List<Appointment>

    fun findStartingWithinWithNoReminderSent(withinHours: Long): List<Appointment>

    fun markReminderSent(id: AppointmentId)

    fun updateStatus(
        id: AppointmentId,
        status: AppointmentStatus,
    )
}
