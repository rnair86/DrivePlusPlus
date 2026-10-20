package org.rnair.driveplusplus.courseservice.web.api

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.courseservice.application.usecase.CancelSessionUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.CreateClassBatchUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.ListBatchesUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.ListSessionsForBatchUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.RescheduleSessionUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.ScheduleSessionUseCase
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.BatchStatus
import org.rnair.driveplusplus.courseservice.core.models.ClassBatch
import org.rnair.driveplusplus.courseservice.core.models.ClassSession
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.SessionId
import org.rnair.driveplusplus.courseservice.core.models.SessionStatus
import org.rnair.driveplusplus.courseservice.web.dtos.CreateBatchRequest
import org.rnair.driveplusplus.courseservice.web.dtos.RescheduleSessionRequest
import org.rnair.driveplusplus.courseservice.web.dtos.ScheduleSessionRequest
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class BatchResourceTest {
    @Test
    fun createsBatchAndReturnsCreatedStatus() {
        val create = mockk<CreateClassBatchUseCase>()
        val courseId = UUID.fromString("00000000-0000-0000-0000-000000007001")
        val instructorId = UUID.fromString("00000000-0000-0000-0000-000000007002")
        val request =
            CreateBatchRequest(courseId, instructorId, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 11), 8)
        every { create.execute(any(), any(), any(), any(), any()) } returns batch(CourseId(courseId), instructorId)

        val response = resource(create = create).create(request)

        assertEquals(201, response.status)
        verify(exactly = 1) {
            create.execute(CourseId(courseId), instructorId, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 11), 8)
        }
    }

    @Test
    fun listsBatchesWithDefaultPageWhenNotProvided() {
        val list = mockk<ListBatchesUseCase>()
        every { list.execute(null, null, null, PageRequest(0, 20)) } returns PageResult(emptyList(), 0, 20, 0)

        val result = resource(list = list).list(null, null, null, null, null)

        assertEquals(0, result.page)
        assertEquals(20, result.size)
    }

    @Test
    fun listsBatchesWithFilters() {
        val list = mockk<ListBatchesUseCase>()
        val courseId = UUID.randomUUID()
        val instructorId = UUID.randomUUID()
        val stored = batch(CourseId(courseId), instructorId)
        every { list.execute(CourseId(courseId), instructorId, BatchStatus.IN_PROGRESS, PageRequest(1, 10)) } returns
            PageResult(listOf(stored), 1, 10, 1)

        val result = resource(list = list).list(courseId, instructorId, BatchStatus.IN_PROGRESS, 1, 10)

        assertEquals(stored.id.value, result.items.single().id)
        assertEquals("SCHEDULED", result.items.single().status)
    }

    @Test
    fun schedulesSessionAndReturnsCreatedStatus() {
        val schedule = mockk<ScheduleSessionUseCase>()
        val batchId = UUID.fromString("00000000-0000-0000-0000-000000007010")
        val request = ScheduleSessionRequest(LocalDate.of(2026, 11, 3), LocalTime.of(9, 0), LocalTime.of(11, 0), "Room 1")
        every { schedule.execute(any(), any(), any(), any(), any()) } returns session(BatchId(batchId))

        val response = resource(schedule = schedule).schedule(batchId, request)

        assertEquals(201, response.status)
        verify(exactly = 1) {
            schedule.execute(BatchId(batchId), LocalDate.of(2026, 11, 3), LocalTime.of(9, 0), LocalTime.of(11, 0), "Room 1")
        }
    }

    @Test
    fun listsSessionsForBatch() {
        val listSessions = mockk<ListSessionsForBatchUseCase>()
        val batchId = UUID.randomUUID()
        every { listSessions.execute(BatchId(batchId)) } returns listOf(session(BatchId(batchId)))

        val result = resource(listSessions = listSessions).listSessions(batchId)

        assertEquals("Room 1", result.single().location)
        assertEquals("PLANNED", result.single().status)
    }

    @Test
    fun reschedulesSession() {
        val reschedule = mockk<RescheduleSessionUseCase>()
        val batchId = UUID.randomUUID()
        val sessionId = UUID.fromString("00000000-0000-0000-0000-000000007020")
        val updated = session(BatchId(batchId), SessionId(sessionId)).copy(sessionDate = LocalDate.of(2026, 11, 10))
        every { reschedule.execute(any(), any(), any(), any()) } returns updated

        val response =
            resource(reschedule = reschedule).reschedule(
                batchId,
                sessionId,
                RescheduleSessionRequest(LocalDate.of(2026, 11, 10), LocalTime.of(13, 0), LocalTime.of(15, 0)),
            )

        assertEquals(LocalDate.of(2026, 11, 10), response.sessionDate)
        verify(exactly = 1) {
            reschedule.execute(SessionId(sessionId), LocalDate.of(2026, 11, 10), LocalTime.of(13, 0), LocalTime.of(15, 0))
        }
    }

    @Test
    fun cancelsSessionAndReturnsNoContent() {
        val cancel = mockk<CancelSessionUseCase>()
        val sessionId = UUID.randomUUID()
        every { cancel.execute(SessionId(sessionId)) } just runs

        val response = resource(cancel = cancel).cancel(UUID.randomUUID(), sessionId)

        assertEquals(204, response.status)
        verify(exactly = 1) { cancel.execute(SessionId(sessionId)) }
    }

    private fun resource(
        create: CreateClassBatchUseCase = mockk(),
        list: ListBatchesUseCase = mockk(),
        listSessions: ListSessionsForBatchUseCase = mockk(),
        schedule: ScheduleSessionUseCase = mockk(),
        reschedule: RescheduleSessionUseCase = mockk(),
        cancel: CancelSessionUseCase = mockk(),
    ) = BatchResource(create, list, listSessions, schedule, reschedule, cancel)

    private fun batch(
        courseId: CourseId,
        instructorId: UUID,
    ) = ClassBatch(
        id = BatchId(UUID.fromString("00000000-0000-0000-0000-000000007010")),
        courseId = courseId,
        instructorId = instructorId,
        startDate = LocalDate.of(2026, 11, 2),
        endDate = LocalDate.of(2026, 12, 11),
        capacity = 8,
    )

    private fun session(
        batchId: BatchId,
        id: SessionId = SessionId(UUID.fromString("00000000-0000-0000-0000-000000007020")),
    ) = ClassSession(
        id = id,
        batchId = batchId,
        sessionDate = LocalDate.of(2026, 11, 3),
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(11, 0),
        location = "Room 1",
        status = SessionStatus.PLANNED,
    )
}
