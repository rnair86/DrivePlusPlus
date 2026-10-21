package org.rnair.driveplusplus.appointmentservice.infrastructure.mappers

import org.rnair.driveplusplus.appointmentservice.core.models.Appointment
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentId
import org.rnair.driveplusplus.appointmentservice.infrastructure.entities.AppointmentJpaEntity

object AppointmentMapper {
    fun toDomain(e: AppointmentJpaEntity) =
        Appointment(
            AppointmentId(e.id),
            e.studentId,
            e.instructorId,
            e.vehicleId,
            e.startAt,
            e.endAt,
            e.pickupLocation,
            e.status,
            e.notes,
            e.reminderSentAt,
        )

    fun toEntity(a: Appointment) =
        AppointmentJpaEntity().apply {
            id = a.id.value
            studentId = a.studentId
            instructorId = a.instructorId
            vehicleId = a.vehicleId
            startAt = a.startAt
            endAt = a.endAt
            pickupLocation = a.pickupLocation
            status = a.status
            notes = a.notes
            reminderSentAt = a.reminderSentAt
        }
}
