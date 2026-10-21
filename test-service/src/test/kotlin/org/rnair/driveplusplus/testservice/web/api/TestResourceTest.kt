package org.rnair.driveplusplus.testservice.web.api

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.testservice.application.usecase.CancelTestUseCase
import org.rnair.driveplusplus.testservice.application.usecase.ListTestsForStudentUseCase
import org.rnair.driveplusplus.testservice.application.usecase.RecordTestResultUseCase
import org.rnair.driveplusplus.testservice.application.usecase.RescheduleTestUseCase
import org.rnair.driveplusplus.testservice.application.usecase.ScheduleTestUseCase
import org.rnair.driveplusplus.testservice.core.exceptions.TestAlreadyCompletedException
import org.rnair.driveplusplus.testservice.core.models.TestResult
import org.rnair.driveplusplus.testservice.core.models.TestSchedule
import org.rnair.driveplusplus.testservice.core.models.TestScheduleId
import org.rnair.driveplusplus.testservice.core.models.TestScheduleStatus
import org.rnair.driveplusplus.testservice.core.models.TestType
import org.rnair.driveplusplus.testservice.web.dtos.RecordTestResultRequest
import org.rnair.driveplusplus.testservice.web.dtos.RescheduleTestRequest
import org.rnair.driveplusplus.testservice.web.dtos.ScheduleTestRequest
import java.time.Instant
import java.util.UUID

class TestResourceTest {
    @Test
    fun schedulesTestAndReturnsCreatedStatus() {
        val schedule = mockk<ScheduleTestUseCase>()
        val studentId = UUID.fromString("00000000-0000-0000-0000-00000000a201")
        val examinerId = UUID.fromString("00000000-0000-0000-0000-00000000a202")
        val scheduledAt = Instant.parse("2026-11-20T15:00:00Z")
        val request = ScheduleTestRequest(studentId, TestType.MOCK_THEORY, scheduledAt, "Room 2", examinerId)
        every { schedule.execute(any(), any(), any(), any(), any()) } returns
            test(studentId, examinerId, scheduledAt)

        val response = resource(schedule = schedule).schedule(request)

        assertEquals(201, response.status)
        verify(exactly = 1) {
            schedule.execute(studentId, TestType.MOCK_THEORY, scheduledAt, "Room 2", examinerId)
        }
    }

    @Test
    fun listsTestsForStudent() {
        val list = mockk<ListTestsForStudentUseCase>()
        val studentId = UUID.randomUUID()
        every { list.execute(studentId) } returns
            listOf(test(studentId, null, Instant.parse("2026-11-20T15:00:00Z")))

        val result = resource(list = list).list(studentId)

        assertEquals(studentId, result.single().studentId)
        assertEquals("MOCK_THEORY", result.single().testType)
        assertEquals("SCHEDULED", result.single().status)
    }

    @Test
    fun reschedulesTest() {
        val reschedule = mockk<RescheduleTestUseCase>()
        val id = UUID.fromString("00000000-0000-0000-0000-00000000a210")
        val newTime = Instant.parse("2026-11-21T15:00:00Z")
        every { reschedule.execute(any(), any()) } returns
            test(UUID.randomUUID(), null, newTime, TestScheduleId(id), TestScheduleStatus.RESCHEDULED)

        val response = resource(reschedule = reschedule).reschedule(id, RescheduleTestRequest(newTime))

        assertEquals(newTime, response.scheduledAt)
        assertEquals("RESCHEDULED", response.status)
        verify(exactly = 1) { reschedule.execute(TestScheduleId(id), newTime) }
    }

    @Test
    fun propagatesCompletedConflict() {
        val reschedule = mockk<RescheduleTestUseCase>()
        val id = UUID.randomUUID()
        every { reschedule.execute(any(), any()) } throws TestAlreadyCompletedException(TestScheduleId(id))

        assertThrows<TestAlreadyCompletedException> {
            resource(reschedule = reschedule).reschedule(id, RescheduleTestRequest(Instant.parse("2026-11-21T15:00:00Z")))
        }
    }

    @Test
    fun cancelsTestAndReturnsNoContent() {
        val cancel = mockk<CancelTestUseCase>()
        val id = UUID.randomUUID()
        every { cancel.execute(TestScheduleId(id)) } just runs

        val response = resource(cancel = cancel).cancel(id)

        assertEquals(204, response.status)
    }

    @Test
    fun recordsResultAndReturnsNoContent() {
        val record = mockk<RecordTestResultUseCase>()
        val id = UUID.randomUUID()
        every { record.execute(TestScheduleId(id), TestResult.PASS, "Clean run") } just runs

        val response = resource(record = record).recordResult(id, RecordTestResultRequest(TestResult.PASS, "Clean run"))

        assertEquals(204, response.status)
        verify(exactly = 1) { record.execute(TestScheduleId(id), TestResult.PASS, "Clean run") }
    }

    private fun resource(
        schedule: ScheduleTestUseCase = mockk(),
        list: ListTestsForStudentUseCase = mockk(),
        reschedule: RescheduleTestUseCase = mockk(),
        cancel: CancelTestUseCase = mockk(),
        record: RecordTestResultUseCase = mockk(),
    ) = TestResource(schedule, list, reschedule, cancel, record)

    private fun test(
        studentId: UUID,
        examinerId: UUID?,
        scheduledAt: Instant,
        id: TestScheduleId = TestScheduleId(UUID.fromString("00000000-0000-0000-0000-00000000a210")),
        status: TestScheduleStatus = TestScheduleStatus.SCHEDULED,
    ) = TestSchedule(
        id = id,
        studentId = studentId,
        testType = TestType.MOCK_THEORY,
        scheduledAt = scheduledAt,
        location = "Room 2",
        examinerOrInstructorId = examinerId,
        status = status,
    )
}
