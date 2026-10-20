package org.rnair.driveplusplus.courseservice.web.dtos

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class SessionResponse(
    val id: UUID,
    val batchId: UUID,
    val sessionDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val location: String?,
    val status: String,
)
