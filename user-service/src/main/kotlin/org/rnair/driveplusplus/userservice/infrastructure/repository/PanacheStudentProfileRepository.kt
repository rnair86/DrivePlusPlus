package org.rnair.driveplusplus.userservice.infrastructure.repository

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.userservice.core.model.StudentProfile
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.repository.StudentProfileRepository
import org.rnair.driveplusplus.userservice.infrastructure.entities.StudentProfileJpaEntity
import org.rnair.driveplusplus.userservice.infrastructure.mappers.StudentProfileMapper
import java.util.UUID

@ApplicationScoped
class PanacheStudentProfileRepository :
    StudentProfileRepository,
    PanacheRepositoryBase<StudentProfileJpaEntity, UUID> {
    override fun save(profile: StudentProfile): StudentProfile {
        val e = StudentProfileMapper.toEntity(profile)
        persist(e)
        return StudentProfileMapper.toDomain(e)
    }

    override fun findByUserId(id: UserId) = findById(id.value)?.let(StudentProfileMapper::toDomain)

    override fun search(
        query: String?,
        page: PageRequest,
    ): PageResult<StudentProfile> {
        val q =
            if (query == null) {
                findAll()
            } else {
                find(
                    "lower(firstName) like ?1 or lower(lastName) like ?1",
                    "%${query.lowercase()}%",
                )
            }
        val items = q.page(page.page, page.size).list().map(StudentProfileMapper::toDomain)
        return PageResult(items, page.page, page.size, q.count())
    }
}
