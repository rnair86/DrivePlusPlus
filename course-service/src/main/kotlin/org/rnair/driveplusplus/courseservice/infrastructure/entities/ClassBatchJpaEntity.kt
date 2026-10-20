package org.rnair.driveplusplus.courseservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.rnair.driveplusplus.courseservice.core.models.BatchStatus
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "class_batches")
class ClassBatchJpaEntity : BaseJpaEntity() {
    @Column(name = "course_id")
    lateinit var courseId: UUID

    @Column(name = "instructor_id")
    lateinit var instructorId: UUID

    @Column(name = "start_date")
    lateinit var startDate: LocalDate

    @Column(name = "end_date")
    lateinit var endDate: LocalDate
    var capacity: Int = 20

    @Enumerated(EnumType.STRING)
    var status: BatchStatus = BatchStatus.SCHEDULED
}
