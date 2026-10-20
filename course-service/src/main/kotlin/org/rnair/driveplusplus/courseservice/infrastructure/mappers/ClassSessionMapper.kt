package org.rnair.driveplusplus.courseservice.infrastructure.mappers

import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.ClassSession
import org.rnair.driveplusplus.courseservice.core.models.SessionId
import org.rnair.driveplusplus.courseservice.infrastructure.entities.ClassSessionJpaEntity

object ClassSessionMapper {
    fun toDomain(e: ClassSessionJpaEntity) =
        ClassSession(SessionId(e.id), BatchId(e.batchId), e.sessionDate, e.startTime, e.endTime, e.location, e.status)

    fun toEntity(s: ClassSession) =
        ClassSessionJpaEntity().apply {
            id = s.id.value
            batchId = s.batchId.value
            sessionDate = s.sessionDate
            startTime = s.startTime
            endTime = s.endTime
            location = s.location
            status = s.status
        }
}
