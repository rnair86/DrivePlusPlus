package org.rnair.driveplusplus.appointmentservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentStatus
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "appointments")
class AppointmentJpaEntity : BaseJpaEntity() {
    @Column(name = "student_id")
    lateinit var studentId: UUID

    @Column(name = "instructor_id")
    lateinit var instructorId: UUID

    @Column(name = "vehicle_id")
    var vehicleId: String? = null

    @Column(name = "start_at")
    lateinit var startAt: Instant

    @Column(name = "end_at")
    lateinit var endAt: Instant

    @Column(name = "pickup_location")
    var pickupLocation: String? = null

    @Enumerated(EnumType.STRING)
    var status: AppointmentStatus = AppointmentStatus.CONFIRMED

    var notes: String? = null

    @Column(name = "reminder_sent_at")
    var reminderSentAt: Instant? = null
}
