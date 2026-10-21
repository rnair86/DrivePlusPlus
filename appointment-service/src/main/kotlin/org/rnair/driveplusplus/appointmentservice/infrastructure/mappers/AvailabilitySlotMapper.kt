package org.rnair.driveplusplus.appointmentservice.infrastructure.mappers

import org.rnair.driveplusplus.appointmentservice.core.models.AvailabilitySlot
import org.rnair.driveplusplus.appointmentservice.core.models.AvailabilitySlotId
import org.rnair.driveplusplus.appointmentservice.infrastructure.entities.AvailabilitySlotJpaEntity

object AvailabilitySlotMapper {
    fun toDomain(e: AvailabilitySlotJpaEntity) =
        AvailabilitySlot(AvailabilitySlotId(e.id), e.instructorId, e.dayOfWeek, e.startTime, e.endTime)

    fun toEntity(s: AvailabilitySlot) =
        AvailabilitySlotJpaEntity().apply {
            id = s.id.value
            instructorId = s.instructorId
            dayOfWeek = s.dayOfWeek
            startTime = s.startTime
            endTime = s.endTime
        }
}
