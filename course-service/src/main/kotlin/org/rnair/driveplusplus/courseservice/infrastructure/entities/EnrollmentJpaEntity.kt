package org.rnair.driveplusplus.courseservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentStatus
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "enrollments")
class EnrollmentJpaEntity : BaseJpaEntity() {
    @Column(name = "student_id")
    lateinit var studentId: UUID

    @Column(name = "batch_id")
    lateinit var batchId: UUID

    @Column(name = "enrolled_at")
    lateinit var enrolledAt: Instant

    @Enumerated(EnumType.STRING)
    var status: EnrollmentStatus = EnrollmentStatus.ACTIVE
}
