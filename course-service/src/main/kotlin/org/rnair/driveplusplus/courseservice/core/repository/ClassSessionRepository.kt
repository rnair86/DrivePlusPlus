package org.rnair.driveplusplus.courseservice.core.repository

import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.ClassSession
import org.rnair.driveplusplus.courseservice.core.models.SessionId

interface ClassSessionRepository {
    fun save(session: ClassSession): ClassSession

    fun findBySessionId(id: SessionId): ClassSession?

    fun listByBatch(batchId: BatchId): List<ClassSession>
}
