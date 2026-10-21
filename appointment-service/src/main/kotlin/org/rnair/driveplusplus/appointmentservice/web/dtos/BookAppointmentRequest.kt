package org.rnair.driveplusplus.appointmentservice.web.dtos

import java.time.Instant
import java.util.UUID

data class BookAppointmentRequest(
    val studentId: UUID,
    val instructorId: UUID,
    val startAt: Instant,
    val endAt: Instant,
    val pickupLocation: String?,
)
