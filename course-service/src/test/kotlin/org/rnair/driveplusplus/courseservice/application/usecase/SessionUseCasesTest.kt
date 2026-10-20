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
import org.rnair.driveplusplus.courseservice.core.exceptions.BatchNotFoundException
import org.rnair.driveplusplus.courseservice.core.exceptions.SessionNotFoundException
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.ClassBatch
import org.rnair.driveplusplus.courseservice.core.models.ClassSession
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.SessionId
import org.rnair.driveplusplus.courseservice.core.models.SessionStatus
import org.rnair.driveplusplus.courseservice.core.repository.ClassBatchRepository
import org.rnair.driveplusplus.courseservice.core.repository.ClassSessionRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class SessionUseCasesTest {
    @Test
    fun schedulesSessionAndPublishesScheduledEvent() {
        val batchId = BatchId(UUID.fromString("00000000-0000-0000-0000-000000004001"))
        val date = LocalDate.of(2026, 11, 3)
        val start = LocalTime.of(9, 0)
        val end = LocalTime.of(11, 0)
        val envelope = slot<EventEnvelope<*>>()
        val batchRepository = mockk<ClassBatchRepository>()
        val sessionRepository = mockk<ClassSessionRepository>()
        val publisher = mockk<DomainEventPublisher>()

        every { batchRepository.findByBatchId(batchId) } returns batch(batchId)
        every { sessionRepository.save(any()) } answers { firstArg() }
        every { publisher.publish(capture(envelope)) } just runs

        val saved = ScheduleSessionUseCase(batchRepository, sessionRepository, publisher).execute(batchId, date, start, end, "Room 1")

        assertEquals(batchId, saved.batchId)
        assertEquals(date, saved.sessionDate)
        assertEquals(start, saved.startTime)
        assertEquals(end, saved.endTime)
        assertEquals("Room 1", saved.location)
        assertEquals(SessionStatus.PLANNED, saved.status)
        assertEquals("class.session.scheduled", envelope.captured.eventType)
        val payload = envelope.captured.payload as SessionScheduledPayload
        assertEquals(saved.id.value, payload.sessionId)
        assertEquals(batchId.value, payload.batchId)
        assertEquals(date, payload.sessionDate)
        verify(exactly = 1) { publisher.publish(any()) }
    }

    @Test
    fun throwsWhenSchedulingAgainstMissingBatch() {
        val batchId = BatchId(UUID.randomUUID())
        val batchRepository = mockk<ClassBatchRepository>()
        val sessionRepository = mockk<ClassSessionRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { batchRepository.findByBatchId(batchId) } returns null

        assertThrows<BatchNotFoundException> {
            ScheduleSessionUseCase(batchRepository, sessionRepository, publisher).execute(
                batchId,
                LocalDate.of(2026, 11, 3),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                null,
            )
        }
        verify(exactly = 0) { sessionRepository.save(any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun reschedulesSessionAndPublishesRescheduledEvent() {
        val session = session()
        val newDate = LocalDate.of(2026, 11, 10)
        val envelope = slot<EventEnvelope<*>>()
        val sessionRepository = mockk<ClassSessionRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { sessionRepository.findBySessionId(session.id) } returns session
        every { sessionRepository.save(any()) } answers { firstArg() }
        every { publisher.publish(capture(envelope)) } just runs

        val updated =
            RescheduleSessionUseCase(sessionRepository, publisher).execute(
                session.id,
                newDate,
                LocalTime.of(13, 0),
                LocalTime.of(15, 0),
            )

        assertEquals(newDate, updated.sessionDate)
        assertEquals(LocalTime.of(13, 0), updated.startTime)
        assertEquals("class.session.rescheduled", envelope.captured.eventType)
        val payload = envelope.captured.payload as SessionRescheduledPayload
        assertEquals(session.id.value, payload.sessionId)
        assertEquals(session.sessionDate, payload.oldDate)
        assertEquals(newDate, payload.newDate)
    }

    @Test
    fun throwsWhenReschedulingMissingSession() {
        val sessionId = SessionId(UUID.randomUUID())
        val sessionRepository = mockk<ClassSessionRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { sessionRepository.findBySessionId(sessionId) } returns null

        assertThrows<SessionNotFoundException> {
            RescheduleSessionUseCase(sessionRepository, publisher).execute(
                sessionId,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(13, 0),
                LocalTime.of(15, 0),
            )
        }
        verify(exactly = 0) { sessionRepository.save(any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun cancelsSessionAndPublishesCancelledEvent() {
        val session = session()
        val envelope = slot<EventEnvelope<*>>()
        val sessionRepository = mockk<ClassSessionRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { sessionRepository.findBySessionId(session.id) } returns session
        every { sessionRepository.save(any()) } answers { firstArg() }
        every { publisher.publish(capture(envelope)) } just runs

        CancelSessionUseCase(sessionRepository, publisher).execute(session.id)

        assertEquals("class.session.cancelled", envelope.captured.eventType)
        @Suppress("UNCHECKED_CAST")
        val payload = envelope.captured.payload as Map<String, UUID>
        assertEquals(session.id.value, payload["sessionId"])
        assertEquals(session.batchId.value, payload["batchId"])
        verify(exactly = 1) { sessionRepository.save(match { it.status == SessionStatus.CANCELLED }) }
    }

    @Test
    fun listsSessionsForABatch() {
        val batchId = BatchId(UUID.randomUUID())
        val expected = listOf(session(batchId))
        val repo = mockk<ClassSessionRepository>()
        every { repo.listByBatch(batchId) } returns expected

        assertEquals(expected, ListSessionsForBatchUseCase(repo).execute(batchId))
    }

    private fun batch(id: BatchId) =
        ClassBatch(
            id = id,
            courseId = CourseId(UUID.randomUUID()),
            instructorId = UUID.randomUUID(),
            startDate = LocalDate.of(2026, 11, 2),
            endDate = LocalDate.of(2026, 12, 11),
        )

    private fun session(batchId: BatchId = BatchId(UUID.randomUUID())) =
        ClassSession(
            id = SessionId(UUID.fromString("00000000-0000-0000-0000-000000004010")),
            batchId = batchId,
            sessionDate = LocalDate.of(2026, 11, 3),
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(11, 0),
            location = "Room 1",
        )
}
