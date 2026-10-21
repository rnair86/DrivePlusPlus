package org.rnair.driveplusplus.testservice.core.models

import java.time.Instant
import java.util.UUID

data class TestSchedule(
    val id: TestScheduleId,
    val studentId: UUID,
    val testType: TestType,
    val scheduledAt: Instant,
    val location: String?,
    val examinerOrInstructorId: UUID?,
    val status: TestScheduleStatus = TestScheduleStatus.SCHEDULED,
    val result: TestResult? = null,
    val resultNotes: String? = null,
    val reminderSentAt: Instant? = null,
)
