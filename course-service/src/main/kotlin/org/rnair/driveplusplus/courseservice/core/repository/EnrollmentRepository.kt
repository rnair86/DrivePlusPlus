package org.rnair.driveplusplus.courseservice.core.repository

import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.Enrollment
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentId
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentStatus
import java.util.UUID

interface EnrollmentRepository {
    fun save(enrollment: Enrollment): Enrollment

    fun countActiveForBatch(batchId: BatchId): Long

    fun existsActive(
        studentId: UUID,
        batchId: BatchId,
    ): Boolean

    fun findByEnrollmentId(id: EnrollmentId): Enrollment?

    fun listByStudent(studentId: UUID): List<Enrollment>

    fun updateStatus(
        id: EnrollmentId,
        status: EnrollmentStatus,
    )
}
