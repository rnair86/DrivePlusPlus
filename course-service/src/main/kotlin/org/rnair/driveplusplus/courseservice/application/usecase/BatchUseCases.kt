package org.rnair.driveplusplus.courseservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import com.rnair.drivingplusplus.commons.paging.PageRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.courseservice.core.exceptions.CourseNotFoundException
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.BatchStatus
import org.rnair.driveplusplus.courseservice.core.models.ClassBatch
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.repository.ClassBatchRepository
import org.rnair.driveplusplus.courseservice.core.repository.CourseRepository
import java.time.LocalDate
import java.util.UUID

data class BatchCreatedPayload(
    val batchId: UUID,
    val courseId: UUID,
    val instructorId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

@ApplicationScoped
class CreateClassBatchUseCase(
    private val courseRepository: CourseRepository,
    private val batchRepository: ClassBatchRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        courseId: CourseId,
        instructorId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        capacity: Int,
    ): ClassBatch {
        courseRepository.findByCourseId(courseId) ?: throw CourseNotFoundException(courseId)
        val batch =
            batchRepository.save(
                ClassBatch(
                    BatchId(UUID.randomUUID()),
                    courseId,
                    instructorId,
                    startDate,
                    endDate,
                    capacity,
                ),
            )
        eventPublisher.publish(
            EventEnvelope(
                eventType = "class.batch.created",
                source = "course-service",
                payload = BatchCreatedPayload(batch.id.value, courseId.value, instructorId, startDate, endDate),
            ),
        )
        return batch
    }
}

@ApplicationScoped
class ListBatchesUseCase(
    private val repo: ClassBatchRepository,
) {
    fun execute(
        courseId: CourseId?,
        instructorId: UUID?,
        status: BatchStatus?,
        page: PageRequest,
    ) = repo.list(courseId, instructorId, status, page)
}
