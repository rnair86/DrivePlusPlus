package org.rnair.driveplusplus.courseservice.infrastructure.repository

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.BatchStatus
import org.rnair.driveplusplus.courseservice.core.models.ClassBatch
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.repository.ClassBatchRepository
import org.rnair.driveplusplus.courseservice.infrastructure.entities.ClassBatchJpaEntity
import org.rnair.driveplusplus.courseservice.infrastructure.mappers.ClassBatchMapper
import java.util.UUID

@ApplicationScoped
class PanacheClassBatchRepository :
    ClassBatchRepository,
    PanacheRepositoryBase<ClassBatchJpaEntity, UUID> {
    override fun save(batch: ClassBatch): ClassBatch {
        val e = ClassBatchMapper.toEntity(batch)
        persist(e)
        return ClassBatchMapper.toDomain(e)
    }

    override fun findByBatchId(id: BatchId) = findById(id.value) ?.let(ClassBatchMapper::toDomain)

    override fun findByIdForUpdate(id: BatchId): ClassBatch? =
        find("id", id.value)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResult()
            ?.let(ClassBatchMapper::toDomain)

    override fun list(
        courseId: CourseId?,
        instructorId: UUID?,
        status: BatchStatus?,
        page: PageRequest,
    ): PageResult<ClassBatch> {
        val clauses = mutableListOf<String>()
        val params = mutableListOf<Any>()
        if (courseId != null) {
            clauses += "courseId = ?${params.size + 1}"
            params += courseId.value
        }
        if (instructorId != null) {
            clauses += "instructorId = ?${params.size + 1}"
            params += instructorId
        }
        if (status != null) {
            clauses += "status = ?${params.size + 1}"
            params += status
        }
        val q = if (clauses.isEmpty()) findAll() else find(clauses.joinToString(" and "), *params.toTypedArray())
        return PageResult(q.page(page.page, page.size).list().map(ClassBatchMapper::toDomain), page.page, page.size, q.count())
    }
}
