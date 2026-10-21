package org.rnair.driveplusplus.appointmentservice.core.models

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class AvailabilitySlot(
    val id: AvailabilitySlotId,
    val instructorId: UUID,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
