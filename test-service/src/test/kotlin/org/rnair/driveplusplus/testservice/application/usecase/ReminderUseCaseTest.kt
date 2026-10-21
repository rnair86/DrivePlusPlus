package org.rnair.driveplusplus.testservice.application.usecase

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
import org.rnair.driveplusplus.testservice.core.models.TestSchedule
import org.rnair.driveplusplus.testservice.core.models.TestScheduleId
import org.rnair.driveplusplus.testservice.core.models.TestType
import org.rnair.driveplusplus.testservice.core.repository.TestScheduleRepository
import java.time.Instant
import java.util.UUID

class ReminderUseCaseTest {
    @Test
    fun publishesReminderAndMarksItSent() {
        val test =
            TestSchedule(
                id = TestScheduleId(UUID.fromString("00000000-0000-0000-0000-00000000a101")),
                studentId = UUID.fromString("00000000-0000-0000-0000-00000000a102"),
                testType = TestType.MOCK_PRACTICAL,
                scheduledAt = Instant.parse("2026-11-20T15:00:00Z"),
                location = "Yard",
                examinerOrInstructorId = null,
            )
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findStartingWithinWithNoReminderSent(24) } returns listOf(test)
        every { publisher.publish(capture(envelope)) } just runs
        every { repo.markReminderSent(test.id) } just runs

        FindDueReminderTestsUseCase(repo, publisher).execute()

        assertEquals("test.reminder.due", envelope.captured.eventType)
        val payload = envelope.captured.payload as TestReminderDuePayload
        assertEquals(test.id.value, payload.testId)
        assertEquals(test.studentId, payload.studentId)
        assertEquals("MOCK_PRACTICAL", payload.testType)
        assertEquals(test.scheduledAt, payload.scheduledAt)
        verify(exactly = 1) { repo.markReminderSent(test.id) }
    }
}
