package org.rnair.driveplusplus.courseservice.application.usecase

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.courseservice.core.exceptions.CourseNotFoundException
import org.rnair.driveplusplus.courseservice.core.models.Course
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.CourseType
import org.rnair.driveplusplus.courseservice.core.repository.CourseRepository
import java.math.BigDecimal
import java.util.UUID

class CourseUseCasesTest {
    @Test
    fun createsCourseFromCommand() {
        val command =
            CreateCourseCommand(
                title = "Theory",
                description = "Classroom theory",
                courseType = CourseType.THEORY,
                durationHours = 12,
                price = BigDecimal("150.00"),
            )
        val repo = mockk<CourseRepository>()
        every { repo.save(any()) } answers { firstArg() }

        val saved = CreateCourseUseCase(repo).execute(command)

        assertEquals(command.title, saved.title)
        assertEquals(command.description, saved.description)
        assertEquals(command.courseType, saved.courseType)
        assertEquals(command.durationHours, saved.durationHours)
        assertEquals(command.price, saved.price)
        assertEquals(true, saved.active)
        verify(exactly = 1) { repo.save(any()) }
    }

    @Test
    fun returnsCourseWhenItExists() {
        val id = CourseId(UUID.fromString("00000000-0000-0000-0000-000000002001"))
        val course = course(id)
        val repo = mockk<CourseRepository>()
        every { repo.findByCourseId(id) } returns course

        assertEquals(course, GetCourseUseCase(repo).execute(id))
        verify(exactly = 1) { repo.findByCourseId(id) }
    }

    @Test
    fun throwsWhenCourseDoesNotExist() {
        val id = CourseId(UUID.fromString("00000000-0000-0000-0000-000000002002"))
        val repo = mockk<CourseRepository>()
        every { repo.findByCourseId(id) } returns null

        assertThrows<CourseNotFoundException> { GetCourseUseCase(repo).execute(id) }
    }

    @Test
    fun listsCoursesWithTheProvidedFilters() {
        val page = PageRequest(page = 1, size = 5)
        val expected = PageResult(items = listOf(course(CourseId(UUID.randomUUID()))), page = 1, size = 5, totalElements = 1)
        val repo = mockk<CourseRepository>()
        every { repo.list(true, CourseType.PRACTICAL, page) } returns expected

        val result = ListCoursesUseCase(repo).execute(true, CourseType.PRACTICAL, page)

        assertEquals(expected, result)
        verify(exactly = 1) { repo.list(true, CourseType.PRACTICAL, page) }
    }

    @Test
    fun updatesCourse() {
        val course = course(CourseId(UUID.randomUUID())).copy(title = "Updated")
        val repo = mockk<CourseRepository>()
        every { repo.save(course) } returns course

        assertEquals(course, UpdateCourseUseCase(repo).execute(course))
        verify(exactly = 1) { repo.save(course) }
    }

    private fun course(id: CourseId) =
        Course(
            id = id,
            title = "Theory",
            description = "Classroom theory",
            courseType = CourseType.THEORY,
            durationHours = 12,
            price = BigDecimal("150.00"),
        )
}
