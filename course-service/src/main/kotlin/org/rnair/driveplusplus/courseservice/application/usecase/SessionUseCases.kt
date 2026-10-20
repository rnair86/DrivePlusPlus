package org.rnair.driveplusplus.courseservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.courseservice.core.exceptions.BatchNotFoundException
import org.rnair.driveplusplus.courseservice.core.exceptions.SessionNotFoundException
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.ClassSession
import org.rnair.driveplusplus.courseservice.core.models.SessionId
import org.rnair.driveplusplus.courseservice.core.models.SessionStatus
import org.rnair.driveplusplus.courseservice.core.repository.ClassBatchRepository
import org.rnair.driveplusplus.courseservice.core.repository.ClassSessionRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class SessionScheduledPayload(
    val sessionId: UUID,
    val batchId: UUID,
    val sessionDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

@ApplicationScoped
class ScheduleSessionUseCase(
    private val batchRepository: ClassBatchRepository,
    private val sessionRepository: ClassSessionRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        batchId: BatchId,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        location: String?,
    ): ClassSession {
        batchRepository.findByBatchId(batchId) ?: throw BatchNotFoundException(batchId)
        val session =
            sessionRepository.save(
                ClassSession(
                    SessionId(UUID.randomUUID()),
                    batchId,
                    date,
                    start,
                    end,
                    location,
                ),
            )
        eventPublisher.publish(
            EventEnvelope(
                eventType = "class.session.scheduled",
                source = "course-service",
                payload = SessionScheduledPayload(session.id.value, batchId.value, date, start, end),
            ),
        )
        return session
    }
}

data class SessionRescheduledPayload(
    val sessionId: UUID,
    val oldDate: LocalDate,
    val newDate: LocalDate,
)

@ApplicationScoped
class RescheduleSessionUseCase(
    private val sessionRepository: ClassSessionRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        sessionId: SessionId,
        newDate: LocalDate,
        newStart: LocalTime,
        newEnd: LocalTime,
    ): ClassSession {
        val existing = sessionRepository.findBySessionId(sessionId) ?: throw SessionNotFoundException(sessionId)
        val updated = sessionRepository.save(existing.copy(sessionDate = newDate, startTime = newStart, endTime = newEnd))
        eventPublisher.publish(
            EventEnvelope(
                eventType = "class.session.rescheduled",
                source = "course-service",
                payload = SessionRescheduledPayload(sessionId.value, existing.sessionDate, newDate),
            ),
        )
        return updated
    }
}

@ApplicationScoped
class CancelSessionUseCase(
    private val sessionRepository: ClassSessionRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(sessionId: SessionId) {
        val existing = sessionRepository.findBySessionId(sessionId) ?: throw SessionNotFoundException(sessionId)
        sessionRepository.save(existing.copy(status = SessionStatus.CANCELLED))
        eventPublisher.publish(
            EventEnvelope(
                eventType = "class.session.cancelled",
                source = "course-service",
                payload = mapOf("sessionId" to sessionId.value, "batchId" to existing.batchId.value),
            ),
        )
    }
}

@ApplicationScoped
class ListSessionsForBatchUseCase(
    private val repo: ClassSessionRepository,
) {
    fun execute(batchId: BatchId): List<ClassSession> = repo.listByBatch(batchId)
}
