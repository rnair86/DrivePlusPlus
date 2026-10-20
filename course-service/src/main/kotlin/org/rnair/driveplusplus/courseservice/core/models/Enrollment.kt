package org.rnair.driveplusplus.courseservice.core.models

import java.time.Instant
import java.util.UUID

data class Enrollment(
    val id: EnrollmentId,
    val studentId: UUID,
    val batchId: BatchId,
    val enrolledAt: Instant,
    val status: EnrollmentStatus = EnrollmentStatus.ACTIVE,
) {
    companion object {
        fun create(
            studentId: UUID,
            batchId: BatchId,
        ) = Enrollment(EnrollmentId(UUID.randomUUID()), studentId, batchId, Instant.now())
    }
}
