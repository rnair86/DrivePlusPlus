package org.rnair.driveplusplus.courseservice.core.repository

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import org.rnair.driveplusplus.courseservice.core.models.Course
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.CourseType

interface CourseRepository {
    fun save(course: Course): Course

    fun findByCourseId(id: CourseId): Course?

    fun list(
        activeOnly: Boolean,
        type: CourseType?,
        page: PageRequest,
    ): PageResult<Course>
}
