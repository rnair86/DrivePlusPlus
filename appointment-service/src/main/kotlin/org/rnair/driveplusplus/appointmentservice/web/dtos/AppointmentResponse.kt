package org.rnair.driveplusplus.appointmentservice.web.dtos

import java.time.Instant
import java.util.UUID

data class AppointmentResponse(
    val id: UUID,
    val studentId: UUID,
    val instructorId: UUID,
    val vehicleId: String?,
    val startAt: Instant,
    val endAt: Instant,
    val pickupLocation: String?,
    val status: String,
    val notes: String?,
    val reminderSentAt: Instant?,
)
