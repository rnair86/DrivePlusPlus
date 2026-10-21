package org.rnair.driveplusplus.appointmentservice.web.dtos

import java.time.LocalTime
import java.util.UUID

data class AvailabilityResponse(
    val id: UUID,
    val instructorId: UUID,
    val dayOfWeek: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
