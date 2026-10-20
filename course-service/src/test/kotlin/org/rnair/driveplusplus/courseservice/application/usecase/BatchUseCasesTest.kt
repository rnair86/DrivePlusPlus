package org.rnair.driveplusplus.courseservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.courseservice.core.exceptions.CourseNotFoundException
import org.rnair.driveplusplus.courseservice.core.models.BatchStatus
import org.rnair.driveplusplus.courseservice.core.models.ClassBatch
import org.rnair.driveplusplus.courseservice.core.models.Course
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.CourseType
import org.rnair.driveplusplus.courseservice.core.repository.ClassBatchRepository
import org.rnair.driveplusplus.courseservice.core.repository.CourseRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class BatchUseCasesTest {
    @Test
    fun createsBatchAndPublishesCreatedEvent() {
        val courseId = CourseId(UUID.fromString("00000000-0000-0000-0000-000000003001"))
        val instructorId = UUID.fromString("00000000-0000-0000-0000-000000003002")
        val start = LocalDate.of(2026, 11, 2)
        val end = LocalDate.of(2026, 12, 11)
        val envelope = slot<EventEnvelope<*>>()
        val courseRepository = mockk<CourseRepository>()
        val batchRepository = mockk<ClassBatchRepository>()
        val publisher = mockk<DomainEventPublisher>()

        every { courseRepository.findByCourseId(courseId) } returns course(courseId)
        every { batchRepository.save(any()) } answers { firstArg() }
        every { publisher.publish(capture(envelope)) } just runs

        val saved = CreateClassBatchUseCase(courseRepository, batchRepository, publisher).execute(courseId, instructorId, start, end, 8)

        assertEquals(courseId, saved.courseId)
        assertEquals(instructorId, saved.instructorId)
        assertEquals(start, saved.startDate)
        assertEquals(end, saved.endDate)
        assertEquals(8, saved.capacity)
        assertEquals(BatchStatus.SCHEDULED, saved.status)
        assertEquals("class.batch.created", envelope.captured.eventType)
        val payload = envelope.captured.payload as BatchCreatedPayload
        assertEquals(saved.id.value, payload.batchId)
        assertEquals(courseId.value, payload.courseId)
        assertEquals(instructorId, payload.instructorId)
        assertEquals(start, payload.startDate)
        assertEquals(end, payload.endDate)
        verify(exactly = 1) { publisher.publish(any()) }
    }

    @Test
    fun throwsWhenCourseDoesNotExistAndPublishesNothing() {
        val courseId = CourseId(UUID.fromString("00000000-0000-0000-0000-000000003003"))
        val courseRepository = mockk<CourseRepository>()
        val batchRepository = mockk<ClassBatchRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { courseRepository.findByCourseId(courseId) } returns null

        assertThrows<CourseNotFoundException> {
            CreateClassBatchUseCase(courseRepository, batchRepository, publisher).execute(
                courseId,
                UUID.randomUUID(),
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 12, 11),
                8,
            )
        }

        verify(exactly = 0) { batchRepository.save(any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun listsBatchesWithTheProvidedFilters() {
        val courseId = CourseId(UUID.randomUUID())
        val instructorId = UUID.randomUUID()
        val page = PageRequest(0, 20)
        val expected = PageResult(items = emptyList<ClassBatch>(), page = 0, size = 20, totalElements = 0)
        val repo = mockk<ClassBatchRepository>()
        every { repo.list(courseId, instructorId, BatchStatus.SCHEDULED, page) } returns expected

        val result = ListBatchesUseCase(repo).execute(courseId, instructorId, BatchStatus.SCHEDULED, page)

        assertEquals(expected, result)
    }

    private fun course(id: CourseId) =
        Course(id, "Theory", "Classroom theory", CourseType.THEORY, 12, BigDecimal("150.00"))
}
