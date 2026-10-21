package org.rnair.driveplusplus.appointmentservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.appointmentservice.core.repository.AppointmentRepository
import java.time.Instant
import java.util.UUID

data class AppointmentReminderDuePayload(
    val appointmentId: UUID,
    val studentId: UUID,
    val instructorId: UUID,
    val startAt: Instant,
    val pickupLocation: String?,
)

@ApplicationScoped
class FindDueReminderAppointmentsUseCase(
    private val appointmentRepository: AppointmentRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute() {
        appointmentRepository.findStartingWithinWithNoReminderSent(withinHours = 24).forEach { appointment ->
            eventPublisher.publish(
                EventEnvelope(
                    eventType = "appointment.reminder.due",
                    source = "appointment-service",
                    payload =
                        AppointmentReminderDuePayload(
                            appointment.id.value,
                            appointment.studentId,
                            appointment.instructorId,
                            appointment.startAt,
                            appointment.pickupLocation,
                        ),
                ),
            )
            appointmentRepository.markReminderSent(appointment.id)
        }
    }
}
