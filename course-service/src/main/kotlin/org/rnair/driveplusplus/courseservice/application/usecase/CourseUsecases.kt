package org.rnair.driveplusplus.courseservice.application.usecase

import com.rnair.drivingplusplus.commons.paging.PageRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.courseservice.core.exceptions.CourseNotFoundException
import org.rnair.driveplusplus.courseservice.core.models.Course
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.CourseType
import org.rnair.driveplusplus.courseservice.core.repository.CourseRepository
import java.math.BigDecimal
import java.util.UUID

data class CreateCourseCommand(
    val title: String,
    val description: String,
    val courseType: CourseType,
    val durationHours: Int,
    val price: BigDecimal,
)

@ApplicationScoped
class CreateCourseUseCase(
    private val repo: CourseRepository,
) {
    @Transactional
    fun execute(cmd: CreateCourseCommand): Course =
        repo.save(Course(CourseId(UUID.randomUUID()), cmd.title, cmd.description, cmd.courseType, cmd.durationHours, cmd.price))
}

@ApplicationScoped
class GetCourseUseCase(
    private val repo: CourseRepository,
) {
    fun execute(id: CourseId): Course = repo.findByCourseId(id) ?: throw CourseNotFoundException(id)
}

@ApplicationScoped
class ListCoursesUseCase(
    private val repo: CourseRepository,
) {
    fun execute(
        activeOnly: Boolean,
        type: CourseType?,
        page: PageRequest,
    ) = repo.list(activeOnly, type, page)
}

@ApplicationScoped
class UpdateCourseUseCase(
    private val repo: CourseRepository,
) {
    @Transactional
    fun execute(course: Course): Course = repo.save(course)
}
