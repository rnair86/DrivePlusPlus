package org.rnair.driveplusplus.appointmentservice.core.models

import java.util.UUID

@JvmInline
value class AppointmentId(
    val value: UUID,
)

@JvmInline
value class AvailabilitySlotId(
    val value: UUID,
)
