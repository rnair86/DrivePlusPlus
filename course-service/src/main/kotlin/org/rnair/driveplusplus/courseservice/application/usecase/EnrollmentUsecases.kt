package org.rnair.driveplusplus.courseservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.courseservice.core.exceptions.AlreadyEnrolledException
import org.rnair.driveplusplus.courseservice.core.exceptions.BatchAtCapacityException
import org.rnair.driveplusplus.courseservice.core.exceptions.BatchNotFoundException
import org.rnair.driveplusplus.courseservice.core.exceptions.CourseNotFoundException
import org.rnair.driveplusplus.courseservice.core.exceptions.EnrollmentNotFoundException
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.Enrollment
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentId
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentStatus
import org.rnair.driveplusplus.courseservice.core.repository.ClassBatchRepository
import org.rnair.driveplusplus.courseservice.core.repository.CourseRepository
import org.rnair.driveplusplus.courseservice.core.repository.EnrollmentRepository
import java.math.BigDecimal
import java.util.UUID

data class EnrollmentCreatedPayload(
    val enrollmentId: UUID,
    val studentId: UUID,
    val batchId: UUID,
    val courseId: UUID,
    val coursePrice: BigDecimal,
)

@ApplicationScoped
class EnrollStudentUseCase(
    private val courseRepository: CourseRepository,
    private val batchRepository: ClassBatchRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        studentId: UUID,
        batchId: BatchId,
    ): Enrollment {
        val batch = batchRepository.findByIdForUpdate(batchId) ?: throw BatchNotFoundException(batchId)
        val course = courseRepository.findByCourseId(batch.courseId) ?: throw CourseNotFoundException(batch.courseId)

        if (enrollmentRepository.countActiveForBatch(batch.id) >= batch.capacity) throw BatchAtCapacityException(batch.id)
        if (enrollmentRepository.existsActive(studentId, batch.id)) throw AlreadyEnrolledException(studentId, batch.id)

        val enrollment = enrollmentRepository.save(Enrollment.create(studentId, batch.id))
        eventPublisher.publish(
            EventEnvelope(
                eventType = "course.enrollment.created",
                source = "course-service",
                payload =
                    EnrollmentCreatedPayload(
                        enrollment.id.value,
                        studentId,
                        batch.id.value,
                        course.id.value,
                        course.price,
                    ),
            ),
        )
        return enrollment
    }
}

@ApplicationScoped
class WithdrawEnrollmentUseCase(
    private val enrollmentRepository: EnrollmentRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(enrollmentId: EnrollmentId) {
        val enrollment = enrollmentRepository.findByEnrollmentId(enrollmentId) ?: throw EnrollmentNotFoundException(enrollmentId)
        enrollmentRepository.updateStatus(enrollmentId, EnrollmentStatus.WITHDRAWN)
        eventPublisher.publish(
            EventEnvelope(
                eventType = "course.enrollment.withdrawn",
                source = "course-service",
                payload =
                    mapOf(
                        "enrollmentId" to enrollmentId.value,
                        "studentId" to enrollment.studentId,
                        "batchId" to enrollment.batchId.value,
                    ),
            ),
        )
    }
}

@ApplicationScoped
class ListEnrollmentsForStudentUseCase(
    private val repo: EnrollmentRepository,
) {
    fun execute(studentId: UUID): List<Enrollment> = repo.listByStudent(studentId)
}
