package org.rnair.driveplusplus.courseservice.infrastructure.repository

import com.rnair.drivingplusplus.commons.paging.PageRequest
import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.BatchStatus
import org.rnair.driveplusplus.courseservice.core.models.ClassBatch
import org.rnair.driveplusplus.courseservice.core.models.Course
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.CourseType
import org.rnair.driveplusplus.courseservice.core.models.Enrollment
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@QuarkusTest
class PanacheCourseRepositoriesTest {
    @Test
    @TestTransaction
    fun listsBatchesByCourseInstructorAndStatus() {
        val courses = PanacheCourseRepository()
        val batches = PanacheClassBatchRepository()
        val course = courses.save(course("Theory"))
        val other = courses.save(course("Practical", CourseType.PRACTICAL))
        val instructorId = UUID.randomUUID()
        val matching =
            batches.save(
                ClassBatch(
                    BatchId(UUID.randomUUID()),
                    course.id,
                    instructorId,
                    LocalDate.of(2026, 11, 2),
                    LocalDate.of(2026, 12, 11),
                    10,
                    BatchStatus.SCHEDULED,
                ),
            )
        batches.save(
            ClassBatch(
                BatchId(UUID.randomUUID()),
                other.id,
                UUID.randomUUID(),
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 12, 11),
                10,
                BatchStatus.CANCELLED,
            ),
        )

        val byCourse = batches.list(course.id, null, null, PageRequest(0, 20))
        val byInstructor = batches.list(null, instructorId, null, PageRequest(0, 20))
        val byStatus = batches.list(null, null, BatchStatus.CANCELLED, PageRequest(0, 20))

        assertEquals(listOf(matching.id), byCourse.items.map { it.id })
        assertEquals(listOf(matching.id), byInstructor.items.map { it.id })
        assertEquals(BatchStatus.CANCELLED, byStatus.items.single().status)
        assertEquals(other.id, byStatus.items.single().courseId)
    }

    @Test
    @TestTransaction
    fun activeEnrollmentCountsIgnoreWithdrawnRows() {
        val courses = PanacheCourseRepository()
        val batches = PanacheClassBatchRepository()
        val enrollments = PanacheEnrollmentRepository()
        val course = courses.save(course("Combo", CourseType.COMBO))
        val batch =
            batches.save(
                ClassBatch(
                    BatchId(UUID.randomUUID()),
                    course.id,
                    UUID.randomUUID(),
                    LocalDate.of(2026, 11, 2),
                    LocalDate.of(2026, 12, 11),
                ),
            )
        val studentId = UUID.randomUUID()
        val enrollment = enrollments.save(Enrollment.create(studentId, batch.id))

        assertEquals(1, enrollments.countActiveForBatch(batch.id))
        assertTrue(enrollments.existsActive(studentId, batch.id))

        enrollments.updateStatus(enrollment.id, EnrollmentStatus.WITHDRAWN)

        assertEquals(0, enrollments.countActiveForBatch(batch.id))
        assertFalse(enrollments.existsActive(studentId, batch.id))
    }

    private fun course(
        title: String,
        type: CourseType = CourseType.THEORY,
    ) = Course(
        id = CourseId(UUID.randomUUID()),
        title = title,
        description = title,
        courseType = type,
        durationHours = 10,
        price = BigDecimal("100.00"),
    )
}
