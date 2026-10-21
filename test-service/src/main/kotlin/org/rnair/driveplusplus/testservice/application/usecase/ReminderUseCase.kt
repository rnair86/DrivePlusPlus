package org.rnair.driveplusplus.testservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.testservice.core.repository.TestScheduleRepository
import java.time.Instant
import java.util.UUID

data class TestReminderDuePayload(
    val testId: UUID,
    val studentId: UUID,
    val testType: String,
    val scheduledAt: Instant,
)

@ApplicationScoped
class FindDueReminderTestsUseCase(
    private val testScheduleRepository: TestScheduleRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute() {
        testScheduleRepository.findStartingWithinWithNoReminderSent(withinHours = 24).forEach { test ->
            eventPublisher.publish(
                EventEnvelope(
                    eventType = "test.reminder.due",
                    source = "test-service",
                    payload = TestReminderDuePayload(test.id.value, test.studentId, test.testType.name, test.scheduledAt),
                ),
            )
            testScheduleRepository.markReminderSent(test.id)
        }
    }
}
