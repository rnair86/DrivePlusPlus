package org.rnair.driveplusplus.courseservice.web.api

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.courseservice.application.usecase.CreateCourseUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.GetCourseUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.ListCoursesUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.UpdateCourseUseCase
import org.rnair.driveplusplus.courseservice.core.exceptions.CourseNotFoundException
import org.rnair.driveplusplus.courseservice.core.models.Course
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.CourseType
import org.rnair.driveplusplus.courseservice.web.dtos.CreateCourseRequest
import java.math.BigDecimal
import java.util.UUID

class CourseResourceTest {
    @Test
    fun createsCourseAndReturnsCreatedStatus() {
        val create = mockk<CreateCourseUseCase>()
        val request = request()
        every { create.execute(any()) } returns course()

        val response = resource(create = create).create(request)

        assertEquals(201, response.status)
        verify(exactly = 1) {
            create.execute(
                withArg {
                    assertEquals("Theory", it.title)
                    assertEquals("Classroom theory", it.description)
                    assertEquals(CourseType.THEORY, it.courseType)
                    assertEquals(12, it.durationHours)
                    assertEquals(BigDecimal("150.00"), it.price)
                },
            )
        }
    }

    @Test
    fun returnsCourseWhenGettingById() {
        val get = mockk<GetCourseUseCase>()
        val id = UUID.fromString("00000000-0000-0000-0000-000000006001")
        every { get.execute(CourseId(id)) } returns course(CourseId(id))

        val response = resource(get = get).get(id)

        assertEquals(id, response.id)
        assertEquals("Theory", response.title)
        assertEquals("THEORY", response.courseType)
        assertEquals(true, response.active)
    }

    @Test
    fun throwsWhenGettingMissingCourse() {
        val get = mockk<GetCourseUseCase>()
        val id = UUID.randomUUID()
        every { get.execute(CourseId(id)) } throws CourseNotFoundException(CourseId(id))

        assertThrows<CourseNotFoundException> { resource(get = get).get(id) }
    }

    @Test
    fun listsCoursesWithDefaultPageWhenNotProvided() {
        val list = mockk<ListCoursesUseCase>()
        every { list.execute(false, null, PageRequest(0, 20)) } returns PageResult(emptyList(), 0, 20, 0)

        val result = resource(list = list).list(null, null, null, null)

        assertEquals(0, result.page)
        assertEquals(20, result.size)
        assertEquals(0, result.totalElements)
    }

    @Test
    fun listsCoursesWithFilters() {
        val list = mockk<ListCoursesUseCase>()
        val stored = course()
        every { list.execute(true, CourseType.PRACTICAL, PageRequest(2, 5)) } returns PageResult(listOf(stored), 2, 5, 1)

        val result = resource(list = list).list(true, CourseType.PRACTICAL, 2, 5)

        assertEquals(stored.id.value, result.items.single().id)
        assertEquals(1, result.totalElements)
    }

    @Test
    fun updatesCourseFromRequest() {
        val get = mockk<GetCourseUseCase>()
        val update = mockk<UpdateCourseUseCase>()
        val id = UUID.fromString("00000000-0000-0000-0000-000000006002")
        val current = course(CourseId(id))
        val updated = current.copy(title = "Practical", courseType = CourseType.PRACTICAL, price = BigDecimal("200.00"))
        every { get.execute(CourseId(id)) } returns current
        every { update.execute(any()) } returns updated

        val response =
            resource(
                get = get,
                update = update,
            ).update(id, request().copy(title = "Practical", courseType = CourseType.PRACTICAL, price = BigDecimal("200.00")))

        assertEquals("Practical", response.title)
        assertEquals("PRACTICAL", response.courseType)
        verify(exactly = 1) {
            update.execute(
                withArg {
                    assertEquals(id, it.id.value)
                    assertEquals("Practical", it.title)
                    assertEquals(CourseType.PRACTICAL, it.courseType)
                    assertEquals(BigDecimal("200.00"), it.price)
                    assertEquals(true, it.active)
                },
            )
        }
    }

    private fun resource(
        create: CreateCourseUseCase = mockk(),
        get: GetCourseUseCase = mockk(),
        list: ListCoursesUseCase = mockk(),
        update: UpdateCourseUseCase = mockk(),
    ) = CourseResource(create, get, list, update)

    private fun request() =
        CreateCourseRequest(
            title = "Theory",
            description = "Classroom theory",
            courseType = CourseType.THEORY,
            durationHours = 12,
            price = BigDecimal("150.00"),
        )

    private fun course(id: CourseId = CourseId(UUID.fromString("00000000-0000-0000-0000-000000006001"))) =
        Course(id, "Theory", "Classroom theory", CourseType.THEORY, 12, BigDecimal("150.00"))
}
