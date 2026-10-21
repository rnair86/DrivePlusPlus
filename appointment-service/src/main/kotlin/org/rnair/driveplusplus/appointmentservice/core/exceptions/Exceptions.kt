package org.rnair.driveplusplus.appointmentservice.core.exceptions

import com.rnair.drivingplusplus.commons.error.ConflictException
import com.rnair.drivingplusplus.commons.error.NotFoundException
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentId
import java.util.UUID

class AppointmentNotFoundException(
    id: AppointmentId,
) : NotFoundException("Appointment not found: ${id.value}")

class SlotConflictException(
    instructorId: UUID,
) : ConflictException("Instructor $instructorId already booked for that time")
