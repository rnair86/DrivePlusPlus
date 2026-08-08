package org.rnair.driveplusplus.userservice.infrastructure.repository

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.userservice.core.model.InstructorProfile
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.repository.InstructorProfileRepository
import org.rnair.driveplusplus.userservice.infrastructure.entities.InstructorProfileJpaEntity
import org.rnair.driveplusplus.userservice.infrastructure.mappers.InstructorProfileMapper
import java.util.UUID

@ApplicationScoped
class PanacheInstructorProfileRepository :
    InstructorProfileRepository,
    PanacheRepositoryBase<InstructorProfileJpaEntity, UUID> {
    override fun save(profile: InstructorProfile): InstructorProfile {
        val e = InstructorProfileMapper.toEntity(profile)
        persist(e)
        return InstructorProfileMapper.toDomain(e)
    }

    override fun findByUserId(id: UserId) = findById(id.value)?.let(InstructorProfileMapper::toDomain)

    override fun findAll(page: PageRequest): PageResult<InstructorProfile> {
        val q = findAll()
        val items = q.page(page.page, page.size).list().map(InstructorProfileMapper::toDomain)
        return PageResult(items, page.page, page.size, q.count())
    }
}
