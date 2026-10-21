package org.rnair.driveplusplus.testservice.web.dtos

import org.rnair.driveplusplus.testservice.core.models.TestType
import java.time.Instant
import java.util.UUID

data class ScheduleTestRequest(
    val studentId: UUID,
    val testType: TestType,
    val scheduledAt: Instant,
    val location: String?,
    val examinerOrInstructorId: UUID?,
)
