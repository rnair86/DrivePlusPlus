package org.rnair.driveplusplus.courseservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.rnair.driveplusplus.courseservice.core.models.SessionStatus
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "class_sessions")
class ClassSessionJpaEntity : BaseJpaEntity() {
    @Column(name = "batch_id")
    lateinit var batchId: UUID

    @Column(name = "session_date")
    lateinit var sessionDate: LocalDate

    @Column(name = "start_time")
    lateinit var startTime: LocalTime

    @Column(name = "end_time")
    lateinit var endTime: LocalTime
    var location: String? = null

    @Enumerated(EnumType.STRING)
    var status: SessionStatus = SessionStatus.PLANNED
}
