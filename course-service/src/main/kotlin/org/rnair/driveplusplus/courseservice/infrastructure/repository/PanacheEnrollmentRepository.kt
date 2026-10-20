package org.rnair.driveplusplus.courseservice.infrastructure.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.Enrollment
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentId
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentStatus
import org.rnair.driveplusplus.courseservice.core.repository.EnrollmentRepository
import org.rnair.driveplusplus.courseservice.infrastructure.entities.EnrollmentJpaEntity
import org.rnair.driveplusplus.courseservice.infrastructure.mappers.EnrollmentMapper
import java.util.UUID

@ApplicationScoped
class PanacheEnrollmentRepository :
    EnrollmentRepository,
    PanacheRepositoryBase<EnrollmentJpaEntity, UUID> {
    override fun save(enrollment: Enrollment): Enrollment {
        val e = EnrollmentMapper.toEntity(enrollment)
        persist(e)
        return EnrollmentMapper.toDomain(e)
    }

    override fun countActiveForBatch(batchId: BatchId) = count("batchId = ?1 and status = ?2", batchId.value, EnrollmentStatus.ACTIVE)

    override fun existsActive(
        studentId: UUID,
        batchId: BatchId,
    ) = count("studentId = ?1 and batchId = ?2 and status = ?3", studentId, batchId.value, EnrollmentStatus.ACTIVE) > 0

    override fun findByEnrollmentId(id: EnrollmentId) = findById(id.value)?.let(EnrollmentMapper::toDomain)

    override fun listByStudent(studentId: UUID) = find("studentId", studentId).list().map(EnrollmentMapper::toDomain)

    override fun updateStatus(
        id: EnrollmentId,
        status: EnrollmentStatus,
    ) {
        update("status = ?1 where id = ?2", status, id.value)
    }
}
