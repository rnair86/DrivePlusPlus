package org.rnair.driveplusplus.courseservice.web.dtos

import java.time.Instant
import java.util.UUID

data class EnrollmentResponse(
    val id: UUID,
    val studentId: UUID,
    val batchId: UUID,
    val enrolledAt: Instant,
    val status: String,
)
