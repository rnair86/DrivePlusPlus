package org.rnair.driveplusplus.courseservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.courseservice.core.exceptions.AlreadyEnrolledException
import org.rnair.driveplusplus.courseservice.core.exceptions.BatchAtCapacityException
import org.rnair.driveplusplus.courseservice.core.exceptions.BatchNotFoundException
import org.rnair.driveplusplus.courseservice.core.exceptions.CourseNotFoundException
import org.rnair.driveplusplus.courseservice.core.exceptions.EnrollmentNotFoundException
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.ClassBatch
import org.rnair.driveplusplus.courseservice.core.models.Course
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.CourseType
import org.rnair.driveplusplus.courseservice.core.models.Enrollment
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentId
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentStatus
import org.rnair.driveplusplus.courseservice.core.repository.ClassBatchRepository
import org.rnair.driveplusplus.courseservice.core.repository.CourseRepository
import org.rnair.driveplusplus.courseservice.core.repository.EnrollmentRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class EnrollmentUseCasesTest {
    @Test
    fun enrollsStudentAndPublishesCreatedEvent() {
        val studentId = UUID.fromString("00000000-0000-0000-0000-000000005001")
        val batch = batch(capacity = 2)
        val course = course(batch.courseId, BigDecimal("499.50"))
        val envelope = slot<EventEnvelope<*>>()
        val repos = repos()

        every { repos.batches.findByIdForUpdate(batch.id) } returns batch
        every { repos.courses.findByCourseId(batch.courseId) } returns course
        every { repos.enrollments.countActiveForBatch(batch.id) } returns 1
        every { repos.enrollments.existsActive(studentId, batch.id) } returns false
        every { repos.enrollments.save(any()) } answers { firstArg() }
        every { repos.publisher.publish(capture(envelope)) } just runs

        val saved = EnrollStudentUseCase(repos.courses, repos.batches, repos.enrollments, repos.publisher).execute(studentId, batch.id)

        assertEquals(studentId, saved.studentId)
        assertEquals(batch.id, saved.batchId)
        assertEquals(EnrollmentStatus.ACTIVE, saved.status)
        assertEquals("course.enrollment.created", envelope.captured.eventType)
        val payload = envelope.captured.payload as EnrollmentCreatedPayload
        assertEquals(saved.id.value, payload.enrollmentId)
        assertEquals(studentId, payload.studentId)
        assertEquals(batch.id.value, payload.batchId)
        assertEquals(course.id.value, payload.courseId)
        assertEquals(course.price, payload.coursePrice)
    }

    @Test
    fun throwsWhenBatchDoesNotExist() {
        val batchId = BatchId(UUID.randomUUID())
        val repos = repos()
        every { repos.batches.findByIdForUpdate(batchId) } returns null

        assertThrows<BatchNotFoundException> {
            EnrollStudentUseCase(repos.courses, repos.batches, repos.enrollments, repos.publisher).execute(UUID.randomUUID(), batchId)
        }
        verify(exactly = 0) { repos.enrollments.save(any()) }
        verify(exactly = 0) { repos.publisher.publish(any()) }
    }

    @Test
    fun throwsWhenCourseForBatchDoesNotExist() {
        val batch = batch(capacity = 2)
        val repos = repos()
        every { repos.batches.findByIdForUpdate(batch.id) } returns batch
        every { repos.courses.findByCourseId(batch.courseId) } returns null

        assertThrows<CourseNotFoundException> {
            EnrollStudentUseCase(repos.courses, repos.batches, repos.enrollments, repos.publisher).execute(UUID.randomUUID(), batch.id)
        }
        verify(exactly = 0) { repos.enrollments.save(any()) }
        verify(exactly = 0) { repos.publisher.publish(any()) }
    }

    @Test
    fun throwsWhenBatchIsAtCapacityAndSavesNothing() {
        val batch = batch(capacity = 1)
        val repos = repos()
        every { repos.batches.findByIdForUpdate(batch.id) } returns batch
        every { repos.courses.findByCourseId(batch.courseId) } returns course(batch.courseId, BigDecimal.TEN)
        every { repos.enrollments.countActiveForBatch(batch.id) } returns 1

        assertThrows<BatchAtCapacityException> {
            EnrollStudentUseCase(repos.courses, repos.batches, repos.enrollments, repos.publisher).execute(UUID.randomUUID(), batch.id)
        }
        verify(exactly = 0) { repos.enrollments.save(any()) }
        verify(exactly = 0) { repos.publisher.publish(any()) }
    }

    @Test
    fun throwsWhenStudentIsAlreadyEnrolledAndSavesNothing() {
        val studentId = UUID.randomUUID()
        val batch = batch(capacity = 5)
        val repos = repos()
        every { repos.batches.findByIdForUpdate(batch.id) } returns batch
        every { repos.courses.findByCourseId(batch.courseId) } returns course(batch.courseId, BigDecimal.TEN)
        every { repos.enrollments.countActiveForBatch(batch.id) } returns 1
        every { repos.enrollments.existsActive(studentId, batch.id) } returns true

        assertThrows<AlreadyEnrolledException> {
            EnrollStudentUseCase(repos.courses, repos.batches, repos.enrollments, repos.publisher).execute(studentId, batch.id)
        }
        verify(exactly = 0) { repos.enrollments.save(any()) }
        verify(exactly = 0) { repos.publisher.publish(any()) }
    }

    @Test
    fun withdrawsEnrollmentAndPublishesWithdrawnEvent() {
        val enrollment = Enrollment(EnrollmentId(UUID.randomUUID()), UUID.randomUUID(), BatchId(UUID.randomUUID()), Instant.parse("2026-10-20T12:00:00Z"))
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<EnrollmentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByEnrollmentId(enrollment.id) } returns enrollment
        every { repo.updateStatus(enrollment.id, EnrollmentStatus.WITHDRAWN) } just runs
        every { publisher.publish(capture(envelope)) } just runs

        WithdrawEnrollmentUseCase(repo, publisher).execute(enrollment.id)

        assertEquals("course.enrollment.withdrawn", envelope.captured.eventType)
        @Suppress("UNCHECKED_CAST")
        val payload = envelope.captured.payload as Map<String, UUID>
        assertEquals(enrollment.id.value, payload["enrollmentId"])
        assertEquals(enrollment.studentId, payload["studentId"])
        assertEquals(enrollment.batchId.value, payload["batchId"])
        verify(exactly = 1) { repo.updateStatus(enrollment.id, EnrollmentStatus.WITHDRAWN) }
    }

    @Test
    fun throwsWhenWithdrawingMissingEnrollment() {
        val enrollmentId = EnrollmentId(UUID.randomUUID())
        val repo = mockk<EnrollmentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByEnrollmentId(enrollmentId) } returns null

        assertThrows<EnrollmentNotFoundException> { WithdrawEnrollmentUseCase(repo, publisher).execute(enrollmentId) }
        verify(exactly = 0) { repo.updateStatus(any(), any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun listsEnrollmentsForStudent() {
        val studentId = UUID.randomUUID()
        val expected = listOf(Enrollment(EnrollmentId(UUID.randomUUID()), studentId, BatchId(UUID.randomUUID()), Instant.now()))
        val repo = mockk<EnrollmentRepository>()
        every { repo.listByStudent(studentId) } returns expected

        assertEquals(expected, ListEnrollmentsForStudentUseCase(repo).execute(studentId))
    }

    private fun repos() =
        EnrollmentRepos(
            courses = mockk(),
            batches = mockk(),
            enrollments = mockk(),
            publisher = mockk(),
        )

    private fun batch(capacity: Int) =
        ClassBatch(
            id = BatchId(UUID.fromString("00000000-0000-0000-0000-000000005010")),
            courseId = CourseId(UUID.fromString("00000000-0000-0000-0000-000000005011")),
            instructorId = UUID.randomUUID(),
            startDate = LocalDate.of(2026, 11, 2),
            endDate = LocalDate.of(2026, 12, 11),
            capacity = capacity,
        )

    private fun course(
        id: CourseId,
        price: BigDecimal,
    ) = Course(id, "Combo", "Theory and practical", CourseType.COMBO, 30, price)
}

private data class EnrollmentRepos(
    val courses: CourseRepository,
    val batches: ClassBatchRepository,
    val enrollments: EnrollmentRepository,
    val publisher: DomainEventPublisher,
)
