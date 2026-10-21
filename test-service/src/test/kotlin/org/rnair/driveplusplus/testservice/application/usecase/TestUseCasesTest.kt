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
import org.junit.jupiter.api.assertThrows
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

class TestUseCasesTest {
    @Test
    fun schedulesTestAndPublishesScheduledEvent() {
        val studentId = UUID.fromString("00000000-0000-0000-0000-00000000a001")
        val examinerId = UUID.fromString("00000000-0000-0000-0000-00000000a002")
        val scheduledAt = Instant.parse("2026-11-20T15:00:00Z")
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.save(any()) } answers { firstArg() }
        every { publisher.publish(capture(envelope)) } just runs

        val saved =
            ScheduleTestUseCase(repo, publisher).execute(
                studentId,
                TestType.MOCK_THEORY,
                scheduledAt,
                "Room 2",
                examinerId,
            )

        assertEquals(studentId, saved.studentId)
        assertEquals(TestType.MOCK_THEORY, saved.testType)
        assertEquals(scheduledAt, saved.scheduledAt)
        assertEquals("Room 2", saved.location)
        assertEquals(examinerId, saved.examinerOrInstructorId)
        assertEquals(TestScheduleStatus.SCHEDULED, saved.status)
        assertEquals("test.scheduled", envelope.captured.eventType)
        val payload = envelope.captured.payload as TestScheduledPayload
        assertEquals(saved.id.value, payload.testId)
        assertEquals(studentId, payload.studentId)
        assertEquals("MOCK_THEORY", payload.testType)
        assertEquals(scheduledAt, payload.scheduledAt)
    }

    @Test
    fun reschedulesTestAndPublishesEvent() {
        val existing = schedule()
        val newTime = Instant.parse("2026-11-21T15:00:00Z")
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByTestScheduleId(existing.id) } returns existing
        every { repo.save(any()) } answers { firstArg() }
        every { publisher.publish(capture(envelope)) } just runs

        val updated = RescheduleTestUseCase(repo, publisher).execute(existing.id, newTime)

        assertEquals(newTime, updated.scheduledAt)
        assertEquals(TestScheduleStatus.RESCHEDULED, updated.status)
        assertEquals("test.rescheduled", envelope.captured.eventType)
        @Suppress("UNCHECKED_CAST")
        val payload = envelope.captured.payload as Map<String, Any>
        assertEquals(existing.id.value, payload["testId"])
        assertEquals(existing.scheduledAt, payload["oldTime"])
        assertEquals(newTime, payload["newTime"])
    }

    @Test
    fun reschedulesACancelledTest() {
        val existing = schedule(status = TestScheduleStatus.CANCELLED)
        val newTime = Instant.parse("2026-11-22T15:00:00Z")
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByTestScheduleId(existing.id) } returns existing
        every { repo.save(any()) } answers { firstArg() }
        every { publisher.publish(any()) } just runs

        val updated = RescheduleTestUseCase(repo, publisher).execute(existing.id, newTime)

        assertEquals(TestScheduleStatus.RESCHEDULED, updated.status)
        assertEquals(newTime, updated.scheduledAt)
    }

    @Test
    fun throwsWhenReschedulingACompletedTestAndPublishesNothing() {
        val existing = schedule(status = TestScheduleStatus.COMPLETED)
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByTestScheduleId(existing.id) } returns existing

        assertThrows<TestAlreadyCompletedException> {
            RescheduleTestUseCase(repo, publisher).execute(existing.id, Instant.parse("2026-11-21T15:00:00Z"))
        }
        verify(exactly = 0) { repo.save(any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun throwsWhenReschedulingAMissingTest() {
        val id = TestScheduleId(UUID.randomUUID())
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByTestScheduleId(id) } returns null

        assertThrows<TestScheduleNotFoundException> {
            RescheduleTestUseCase(repo, publisher).execute(id, Instant.parse("2026-11-21T15:00:00Z"))
        }
    }

    @Test
    fun cancelsTestAndPublishesEvent() {
        val existing = schedule()
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByTestScheduleId(existing.id) } returns existing
        every { repo.updateStatus(existing.id, TestScheduleStatus.CANCELLED) } just runs
        every { publisher.publish(capture(envelope)) } just runs

        CancelTestUseCase(repo, publisher).execute(existing.id)

        assertEquals("test.cancelled", envelope.captured.eventType)
        @Suppress("UNCHECKED_CAST")
        val payload = envelope.captured.payload as Map<String, UUID>
        assertEquals(existing.id.value, payload["testId"])
        assertEquals(existing.studentId, payload["studentId"])
    }

    @Test
    fun throwsWhenCancellingACompletedTest() {
        val existing = schedule(status = TestScheduleStatus.COMPLETED)
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByTestScheduleId(existing.id) } returns existing

        assertThrows<TestAlreadyCompletedException> { CancelTestUseCase(repo, publisher).execute(existing.id) }
        verify(exactly = 0) { repo.updateStatus(any(), any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun throwsWhenCancellingAnAlreadyCancelledTest() {
        val existing = schedule(status = TestScheduleStatus.CANCELLED)
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByTestScheduleId(existing.id) } returns existing

        assertThrows<TestAlreadyCancelledException> { CancelTestUseCase(repo, publisher).execute(existing.id) }
        verify(exactly = 0) { repo.updateStatus(any(), any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun recordsResultAndPublishesEvent() {
        val existing = schedule(status = TestScheduleStatus.RESCHEDULED)
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByTestScheduleId(existing.id) } returns existing
        every { repo.recordResult(existing.id, TestResult.PASS, "Clean run") } just runs
        every { publisher.publish(capture(envelope)) } just runs

        RecordTestResultUseCase(repo, publisher).execute(existing.id, TestResult.PASS, "Clean run")

        assertEquals("test.result.published", envelope.captured.eventType)
        @Suppress("UNCHECKED_CAST")
        val payload = envelope.captured.payload as Map<String, Any>
        assertEquals(existing.id.value, payload["testId"])
        assertEquals(existing.studentId, payload["studentId"])
        assertEquals("PASS", payload["result"])
    }

    @Test
    fun throwsWhenRecordingAResultForACompletedTest() {
        val existing = schedule(status = TestScheduleStatus.COMPLETED)
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByTestScheduleId(existing.id) } returns existing

        assertThrows<TestAlreadyCompletedException> {
            RecordTestResultUseCase(repo, publisher).execute(existing.id, TestResult.FAIL, null)
        }
        verify(exactly = 0) { repo.recordResult(any(), any(), any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun throwsWhenRecordingAResultForACancelledTest() {
        val existing = schedule(status = TestScheduleStatus.CANCELLED)
        val repo = mockk<TestScheduleRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findByTestScheduleId(existing.id) } returns existing

        assertThrows<TestAlreadyCancelledException> {
            RecordTestResultUseCase(repo, publisher).execute(existing.id, TestResult.PASS, null)
        }
        verify(exactly = 0) { repo.recordResult(any(), any(), any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun listsTestsForStudent() {
        val studentId = UUID.randomUUID()
        val expected = listOf(schedule(studentId = studentId))
        val repo = mockk<TestScheduleRepository>()
        every { repo.listByStudent(studentId) } returns expected

        assertEquals(expected, ListTestsForStudentUseCase(repo).execute(studentId))
    }

    private fun schedule(
        status: TestScheduleStatus = TestScheduleStatus.SCHEDULED,
        studentId: UUID = UUID.fromString("00000000-0000-0000-0000-00000000a001"),
    ) = TestSchedule(
        id = TestScheduleId(UUID.fromString("00000000-0000-0000-0000-00000000a010")),
        studentId = studentId,
        testType = TestType.OFFICIAL_ROAD,
        scheduledAt = Instant.parse("2026-11-20T15:00:00Z"),
        location = "Test centre",
        examinerOrInstructorId = UUID.fromString("00000000-0000-0000-0000-00000000a002"),
        status = status,
    )
}
