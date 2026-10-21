package org.rnair.driveplusplus.testservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.rnair.driveplusplus.testservice.core.models.TestResult
import org.rnair.driveplusplus.testservice.core.models.TestScheduleStatus
import org.rnair.driveplusplus.testservice.core.models.TestType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "test_schedules")
class TestScheduleJpaEntity : BaseJpaEntity() {
    @Column(name = "student_id")
    lateinit var studentId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type")
    lateinit var testType: TestType

    @Column(name = "scheduled_at")
    lateinit var scheduledAt: Instant

    var location: String? = null

    @Column(name = "examiner_or_instructor_id")
    var examinerOrInstructorId: UUID? = null

    @Enumerated(EnumType.STRING)
    var status: TestScheduleStatus = TestScheduleStatus.SCHEDULED

    @Enumerated(EnumType.STRING)
    var result: TestResult? = null

    @Column(name = "result_notes")
    var resultNotes: String? = null

    @Column(name = "reminder_sent_at")
    var reminderSentAt: Instant? = null
}
