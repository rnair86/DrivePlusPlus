package org.rnair.driveplusplus.testservice.core.repository

import org.rnair.driveplusplus.testservice.core.models.TestResult
import org.rnair.driveplusplus.testservice.core.models.TestSchedule
import org.rnair.driveplusplus.testservice.core.models.TestScheduleId
import org.rnair.driveplusplus.testservice.core.models.TestScheduleStatus
import java.util.UUID

interface TestScheduleRepository {
    fun save(test: TestSchedule): TestSchedule

    fun findByTestScheduleId(id: TestScheduleId): TestSchedule?

    fun listByStudent(studentId: UUID): List<TestSchedule>

    fun findStartingWithinWithNoReminderSent(withinHours: Long): List<TestSchedule>

    fun markReminderSent(id: TestScheduleId)

    fun updateStatus(
        id: TestScheduleId,
        status: TestScheduleStatus,
    )

    fun recordResult(
        id: TestScheduleId,
        result: TestResult,
        notes: String?,
    )
}
