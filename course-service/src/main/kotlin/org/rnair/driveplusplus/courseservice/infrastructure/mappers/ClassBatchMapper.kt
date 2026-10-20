package org.rnair.driveplusplus.courseservice.infrastructure.mappers

import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.ClassBatch
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.infrastructure.entities.ClassBatchJpaEntity

object ClassBatchMapper {
    fun toDomain(e: ClassBatchJpaEntity) =
        ClassBatch(BatchId(e.id), CourseId(e.courseId), e.instructorId, e.startDate, e.endDate, e.capacity, e.status)

    fun toEntity(b: ClassBatch) =
        ClassBatchJpaEntity().apply {
            id = b.id.value
            courseId = b.courseId.value
            instructorId = b.instructorId
            startDate = b.startDate
            endDate = b.endDate
            capacity = b.capacity
            status = b.status
        }
}
