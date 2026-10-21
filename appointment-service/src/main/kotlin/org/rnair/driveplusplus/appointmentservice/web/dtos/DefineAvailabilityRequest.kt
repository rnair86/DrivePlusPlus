package org.rnair.driveplusplus.appointmentservice.web.dtos

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class DefineAvailabilityRequest(
    val instructorId: UUID,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
