package org.rnair.driveplusplus.appointmentservice.web.dtos

import java.time.Instant

data class RescheduleAppointmentRequest(
    val startAt: Instant,
    val endAt: Instant,
)
