package org.rnair.driveplusplus.courseservice.infrastructure.mappers

import org.rnair.driveplusplus.courseservice.core.models.Course
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.infrastructure.entities.CourseJpaEntity

object CourseMapper {
    fun toDomain(e: CourseJpaEntity) = Course(CourseId(e.id), e.title, e.description, e.courseType, e.durationHours, e.price, e.active)

    fun toEntity(c: Course) =
        CourseJpaEntity().apply {
            id = c.id.value
            title = c.title
            description = c.description
            courseType = c.courseType
            durationHours = c.durationHours
            price = c.price
            active = c.active
        }
}
