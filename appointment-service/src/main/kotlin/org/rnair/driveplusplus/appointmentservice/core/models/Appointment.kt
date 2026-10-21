package org.rnair.driveplusplus.appointmentservice.core.models

import java.time.Instant
import java.util.UUID

data class Appointment(
    val id: AppointmentId,
    val studentId: UUID,
    val instructorId: UUID,
    val vehicleId: String?,
    val startAt: Instant,
    val endAt: Instant,
    val pickupLocation: String?,
    val status: AppointmentStatus = AppointmentStatus.CONFIRMED,
    val notes: String? = null,
    val reminderSentAt: Instant? = null,
)
