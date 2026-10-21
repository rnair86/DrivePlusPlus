package org.rnair.driveplusplus.appointmentservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "instructor_availability")
class AvailabilitySlotJpaEntity : BaseJpaEntity() {
    @Column(name = "instructor_id")
    lateinit var instructorId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    lateinit var dayOfWeek: DayOfWeek

    @Column(name = "start_time")
    lateinit var startTime: LocalTime

    @Column(name = "end_time")
    lateinit var endTime: LocalTime
}
