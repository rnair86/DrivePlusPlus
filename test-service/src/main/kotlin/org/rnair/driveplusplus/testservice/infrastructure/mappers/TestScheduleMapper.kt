package org.rnair.driveplusplus.testservice.infrastructure.mappers

import org.rnair.driveplusplus.testservice.core.models.TestSchedule
import org.rnair.driveplusplus.testservice.core.models.TestScheduleId
import org.rnair.driveplusplus.testservice.infrastructure.entities.TestScheduleJpaEntity

object TestScheduleMapper {
    fun toDomain(e: TestScheduleJpaEntity) =
        TestSchedule(
            TestScheduleId(e.id),
            e.studentId,
            e.testType,
            e.scheduledAt,
            e.location,
            e.examinerOrInstructorId,
            e.status,
            e.result,
            e.resultNotes,
            e.reminderSentAt,
        )

    fun toEntity(t: TestSchedule) =
        TestScheduleJpaEntity().apply {
            id = t.id.value
            studentId = t.studentId
            testType = t.testType
            scheduledAt = t.scheduledAt
            location = t.location
            examinerOrInstructorId = t.examinerOrInstructorId
            status = t.status
            result = t.result
            resultNotes = t.resultNotes
            reminderSentAt = t.reminderSentAt
        }
}
