package org.rnair.driveplusplus.testservice.infrastructure.repository

import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.testservice.core.models.TestResult
import org.rnair.driveplusplus.testservice.core.models.TestSchedule
import org.rnair.driveplusplus.testservice.core.models.TestScheduleId
import org.rnair.driveplusplus.testservice.core.models.TestScheduleStatus
import org.rnair.driveplusplus.testservice.core.models.TestType
import java.time.Instant
import java.util.UUID

@QuarkusTest
class PanacheTestScheduleRepositoryTest {
    @Test
    @TestTransaction
    fun recordResultStoresTheResultAndCompletesTheTest() {
        val repo = PanacheTestScheduleRepository()
        val saved = repo.save(schedule(Instant.parse("2026-11-20T15:00:00Z")))

        repo.recordResult(saved.id, TestResult.PASS, "Clean run")
        repo.getEntityManager().clear()

        val stored = repo.findByTestScheduleId(saved.id)
        assertEquals(TestResult.PASS, stored?.result)
        assertEquals("Clean run", stored?.resultNotes)
        assertEquals(TestScheduleStatus.COMPLETED, stored?.status)
    }

    @Test
    @TestTransaction
    fun dueRemindersSkipSentNonScheduledAndFarFutureTests() {
        val repo = PanacheTestScheduleRepository()
        val due = repo.save(schedule(Instant.now().plusSeconds(3600)))
        repo.save(schedule(Instant.now().plusSeconds(3600)).copy(reminderSentAt = Instant.parse("2026-10-01T00:00:00Z")))
        repo.save(schedule(Instant.now().plusSeconds(3600), TestScheduleStatus.RESCHEDULED))
        repo.save(schedule(Instant.now().plusSeconds(3600), TestScheduleStatus.CANCELLED))
        repo.save(schedule(Instant.now().plusSeconds(60 * 60 * 48)))

        val dueIds = repo.findStartingWithinWithNoReminderSent(24).map { it.id }

        assertEquals(listOf(due.id), dueIds)
    }

    @Test
    @TestTransaction
    fun saveUpdatesAnExistingTest() {
        val repo = PanacheTestScheduleRepository()
        val saved = repo.save(schedule(Instant.parse("2026-11-20T15:00:00Z")))
        val newTime = Instant.parse("2026-11-21T15:00:00Z")

        val updated = repo.save(saved.copy(scheduledAt = newTime, status = TestScheduleStatus.RESCHEDULED))

        assertEquals(newTime, repo.findByTestScheduleId(saved.id)?.scheduledAt)
        assertEquals(TestScheduleStatus.RESCHEDULED, updated.status)
        assertNull(updated.result)
    }

    private fun schedule(
        scheduledAt: Instant,
        status: TestScheduleStatus = TestScheduleStatus.SCHEDULED,
    ) = TestSchedule(
        id = TestScheduleId(UUID.randomUUID()),
        studentId = UUID.randomUUID(),
        testType = TestType.OFFICIAL_THEORY,
        scheduledAt = scheduledAt,
        location = "Test centre",
        examinerOrInstructorId = null,
        status = status,
    )
}
