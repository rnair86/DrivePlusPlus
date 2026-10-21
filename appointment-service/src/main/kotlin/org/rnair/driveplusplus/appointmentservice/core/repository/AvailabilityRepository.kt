package org.rnair.driveplusplus.appointmentservice.core.repository

import org.rnair.driveplusplus.appointmentservice.core.models.AvailabilitySlot
import java.util.UUID

interface AvailabilityRepository {
    fun save(slot: AvailabilitySlot): AvailabilitySlot

    fun findForInstructor(instructorId: UUID): List<AvailabilitySlot>
}
