package org.rnair.driveplusplus.courseservice.infrastructure.repository

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.courseservice.core.models.Course
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.CourseType
import org.rnair.driveplusplus.courseservice.core.repository.CourseRepository
import org.rnair.driveplusplus.courseservice.infrastructure.entities.CourseJpaEntity
import org.rnair.driveplusplus.courseservice.infrastructure.mappers.CourseMapper
import java.util.UUID

@ApplicationScoped
class PanacheCourseRepository :
    CourseRepository,
    PanacheRepositoryBase<CourseJpaEntity, UUID> {
    override fun save(course: Course): Course {
        val e = CourseMapper.toEntity(course)
        persist(e)
        return CourseMapper.toDomain(e)
    }

    override fun findByCourseId(id: CourseId): Course? = findById(id.value)?.let(CourseMapper::toDomain)

    override fun list(
        activeOnly: Boolean,
        type: CourseType?,
        page: PageRequest,
    ): PageResult<Course> {
        val q =
            when {
                activeOnly && type != null -> find("active = true and courseType = ?1", type)
                activeOnly -> find("active = true")
                type != null -> find("courseType", type)
                else -> findAll()
            }
        return PageResult(
            q.page(page.page, page.size).list().map(CourseMapper::toDomain),
            page.page,
            page.size,
            q.count(),
        )
    }
}
