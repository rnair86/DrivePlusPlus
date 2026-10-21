package org.rnair.driveplusplus.testservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.testservice.core.exceptions.TestAlreadyCancelledException
import org.rnair.driveplusplus.testservice.core.exceptions.TestAlreadyCompletedException
import org.rnair.driveplusplus.testservice.core.exceptions.TestScheduleNotFoundException
import org.rnair.driveplusplus.testservice.core.models.TestResult
import org.rnair.driveplusplus.testservice.core.models.TestSchedule
import org.rnair.driveplusplus.testservice.core.models.TestScheduleId
import org.rnair.driveplusplus.testservice.core.models.TestScheduleStatus
import org.rnair.driveplusplus.testservice.core.models.TestType
import org.rnair.driveplusplus.testservice.core.repository.TestScheduleRepository
import java.time.Instant
import java.util.UUID

data class TestScheduledPayload(
    val testId: UUID,
    val studentId: UUID,
    val testType: String,
    val scheduledAt: Instant,
)

@ApplicationScoped
class ScheduleTestUseCase(
    private val testScheduleRepository: TestScheduleRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        studentId: UUID,
        testType: TestType,
        scheduledAt: Instant,
        location: String?,
        examinerId: UUID?,
    ): TestSchedule {
        val test =
            testScheduleRepository.save(
                TestSchedule(
                    TestScheduleId(UUID.randomUUID()),
                    studentId,
                    testType,
                    scheduledAt,
                    location,
                    examinerId,
                ),
            )
        eventPublisher.publish(
            EventEnvelope(
                eventType = "test.scheduled",
                source = "test-service",
                payload = TestScheduledPayload(test.id.value, studentId, testType.name, scheduledAt),
            ),
        )
        return test
    }
}

@ApplicationScoped
class RescheduleTestUseCase(
    private val testScheduleRepository: TestScheduleRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        id: TestScheduleId,
        newTime: Instant,
    ): TestSchedule {
        val existing = testScheduleRepository.findByTestScheduleId(id) ?: throw TestScheduleNotFoundException(id)
        if (existing.status == TestScheduleStatus.COMPLETED) throw TestAlreadyCompletedException(id)
        val updated = testScheduleRepository.save(existing.copy(scheduledAt = newTime, status = TestScheduleStatus.RESCHEDULED))
        eventPublisher.publish(
            EventEnvelope(
                eventType = "test.rescheduled",
                source = "test-service",
                payload = mapOf("testId" to id.value, "oldTime" to existing.scheduledAt, "newTime" to newTime),
            ),
        )
        return updated
    }
}

@ApplicationScoped
class CancelTestUseCase(
    private val testScheduleRepository: TestScheduleRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(id: TestScheduleId) {
        val existing = testScheduleRepository.findByTestScheduleId(id) ?: throw TestScheduleNotFoundException(id)
        rejectClosed(existing)
        testScheduleRepository.updateStatus(id, TestScheduleStatus.CANCELLED)
        eventPublisher.publish(
            EventEnvelope(
                eventType = "test.cancelled",
                source = "test-service",
                payload = mapOf("testId" to id.value, "studentId" to existing.studentId),
            ),
        )
    }
}

@ApplicationScoped
class RecordTestResultUseCase(
    private val testScheduleRepository: TestScheduleRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        id: TestScheduleId,
        result: TestResult,
        notes: String?,
    ) {
        val existing = testScheduleRepository.findByTestScheduleId(id) ?: throw TestScheduleNotFoundException(id)
        rejectClosed(existing)
        testScheduleRepository.recordResult(id, result, notes)
        eventPublisher.publish(
            EventEnvelope(
                eventType = "test.result.published",
                source = "test-service",
                payload = mapOf("testId" to id.value, "studentId" to existing.studentId, "result" to result.name),
            ),
        )
    }
}

@ApplicationScoped
class ListTestsForStudentUseCase(
    private val repo: TestScheduleRepository,
) {
    fun execute(studentId: UUID): List<TestSchedule> = repo.listByStudent(studentId)
}

private fun rejectClosed(test: TestSchedule) {
    when (test.status) {
        TestScheduleStatus.COMPLETED -> throw TestAlreadyCompletedException(test.id)
        TestScheduleStatus.CANCELLED -> throw TestAlreadyCancelledException(test.id)
        TestScheduleStatus.SCHEDULED, TestScheduleStatus.RESCHEDULED -> Unit
    }
}
