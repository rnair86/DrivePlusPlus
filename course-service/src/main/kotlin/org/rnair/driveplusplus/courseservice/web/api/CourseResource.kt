package org.rnair.driveplusplus.courseservice.web.api

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.courseservice.application.usecase.CreateCourseCommand
import org.rnair.driveplusplus.courseservice.application.usecase.CreateCourseUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.GetCourseUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.ListCoursesUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.UpdateCourseUseCase
import org.rnair.driveplusplus.courseservice.core.models.Course
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.CourseType
import org.rnair.driveplusplus.courseservice.web.dtos.CourseResponse
import org.rnair.driveplusplus.courseservice.web.dtos.CreateCourseRequest
import java.util.UUID

@Path("/api/v1/courses")
class CourseResource(
    private val createCourseUseCase: CreateCourseUseCase,
    private val getCourseUseCase: GetCourseUseCase,
    private val listCoursesUseCase: ListCoursesUseCase,
    private val updateCourseUseCase: UpdateCourseUseCase,
) {
    @POST
    @RunOnVirtualThread
    fun create(request: CreateCourseRequest): Response {
        val course =
            createCourseUseCase.execute(
                CreateCourseCommand(
                    request.title,
                    request.description,
                    request.courseType,
                    request.durationHours,
                    request.price,
                ),
            )
        return Response.status(201).entity(toResponse(course)).build()
    }

    @GET
    @Path("/{id}")
    @RunOnVirtualThread
    fun get(
        @PathParam("id") id: UUID,
    ): CourseResponse = toResponse(getCourseUseCase.execute(CourseId(id)))

    @GET
    @RunOnVirtualThread
    fun list(
        @QueryParam("activeOnly") activeOnly: Boolean?,
        @QueryParam("type") type: CourseType?,
        @QueryParam("page") page: Int?,
        @QueryParam("size") size: Int?,
    ): PageResult<CourseResponse> {
        val result = listCoursesUseCase.execute(activeOnly ?: false, type, PageRequest(page ?: 0, size ?: 20))
        return PageResult(result.items.map(::toResponse), result.page, result.size, result.totalElements)
    }

    @PUT
    @Path("/{id}")
    @RunOnVirtualThread
    fun update(
        @PathParam("id") id: UUID,
        request: CreateCourseRequest,
    ): CourseResponse {
        val current = getCourseUseCase.execute(CourseId(id))
        return toResponse(
            updateCourseUseCase.execute(
                current.copy(
                    title = request.title,
                    description = request.description,
                    courseType = request.courseType,
                    durationHours = request.durationHours,
                    price = request.price,
                ),
            ),
        )
    }

    private fun toResponse(course: Course) =
        CourseResponse(
            course.id.value,
            course.title,
            course.description,
            course.courseType.name,
            course.durationHours,
            course.price,
            course.active,
        )
}
