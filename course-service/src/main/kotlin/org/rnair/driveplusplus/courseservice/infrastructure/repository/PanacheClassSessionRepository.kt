package org.rnair.driveplusplus.courseservice.infrastructure.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.ClassSession
import org.rnair.driveplusplus.courseservice.core.models.SessionId
import org.rnair.driveplusplus.courseservice.core.repository.ClassSessionRepository
import org.rnair.driveplusplus.courseservice.infrastructure.entities.ClassSessionJpaEntity
import org.rnair.driveplusplus.courseservice.infrastructure.mappers.ClassSessionMapper
import java.util.UUID

@ApplicationScoped
class PanacheClassSessionRepository :
    ClassSessionRepository,
    PanacheRepositoryBase<ClassSessionJpaEntity, UUID> {
    override fun save(session: ClassSession): ClassSession {
        val e = ClassSessionMapper.toEntity(session)
        persist(e)
        return ClassSessionMapper.toDomain(e)
    }

    override fun findBySessionId(id: SessionId) = findById(id.value)?.let(ClassSessionMapper::toDomain)

    override fun listByBatch(batchId: BatchId) = find("batchId", batchId.value).list().map(ClassSessionMapper::toDomain)
}
