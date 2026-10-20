package org.rnair.driveplusplus.courseservice.infrastructure.mappers

import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.Enrollment
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentId
import org.rnair.driveplusplus.courseservice.infrastructure.entities.EnrollmentJpaEntity

object EnrollmentMapper {
    fun toDomain(e: EnrollmentJpaEntity) = Enrollment(EnrollmentId(e.id), e.studentId, BatchId(e.batchId), e.enrolledAt, e.status)

    fun toEntity(en: Enrollment) =
        EnrollmentJpaEntity().apply {
            id = en.id.value
            studentId = en.studentId
            batchId = en.batchId.value
            enrolledAt = en.enrolledAt
            status = en.status
        }
}
