package org.rnair.driveplusplus.courseservice.core.models

import java.time.LocalDate
import java.time.LocalTime

data class ClassSession(
    val id: SessionId,
    val batchId: BatchId,
    val sessionDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val location: String?,
    val status: SessionStatus = SessionStatus.PLANNED,
)
