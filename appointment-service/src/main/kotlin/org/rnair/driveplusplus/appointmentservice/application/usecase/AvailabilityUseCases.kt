package org.rnair.driveplusplus.appointmentservice.application.usecase

import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.appointmentservice.core.models.AvailabilitySlot
import org.rnair.driveplusplus.appointmentservice.core.models.AvailabilitySlotId
import org.rnair.driveplusplus.appointmentservice.core.repository.AvailabilityRepository
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

@ApplicationScoped
class DefineAvailabilityUseCase(
    private val repo: AvailabilityRepository,
) {
    @Transactional
    fun execute(
        instructorId: UUID,
        dayOfWeek: DayOfWeek,
        start: LocalTime,
        end: LocalTime,
    ): AvailabilitySlot =
        repo.save(AvailabilitySlot(AvailabilitySlotId(UUID.randomUUID()), instructorId, dayOfWeek, start, end))
}

@ApplicationScoped
class GetAvailableSlotsUseCase(
    private val repo: AvailabilityRepository,
) {
    fun execute(instructorId: UUID): List<AvailabilitySlot> = repo.findForInstructor(instructorId)
}
