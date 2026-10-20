package org.rnair.driveplusplus.courseservice.web.dtos

import java.time.LocalDate
import java.time.LocalTime

data class RescheduleSessionRequest(
    val sessionDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
