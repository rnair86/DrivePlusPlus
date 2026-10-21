package org.rnair.driveplusplus.testservice.web.dtos

import java.time.Instant
import java.util.UUID

data class TestScheduleResponse(
    val id: UUID,
    val studentId: UUID,
    val testType: String,
    val scheduledAt: Instant,
    val location: String?,
    val examinerOrInstructorId: UUID?,
    val status: String,
    val result: String?,
    val resultNotes: String?,
    val reminderSentAt: Instant?,
)
