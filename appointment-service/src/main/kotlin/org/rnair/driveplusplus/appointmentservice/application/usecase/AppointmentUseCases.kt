package org.rnair.driveplusplus.appointmentservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.appointmentservice.core.exceptions.AppointmentNotFoundException
import org.rnair.driveplusplus.appointmentservice.core.exceptions.SlotConflictException
import org.rnair.driveplusplus.appointmentservice.core.models.Appointment
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentId
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentStatus
import org.rnair.driveplusplus.appointmentservice.core.repository.AppointmentRepository
import java.time.Instant
import java.util.UUID

data class AppointmentCreatedPayload(
    val appointmentId: UUID,
    val studentId: UUID,
    val instructorId: UUID,
    val startAt: Instant,
)

@ApplicationScoped
class BookAppointmentUseCase(
    private val appointmentRepository: AppointmentRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        studentId: UUID,
        instructorId: UUID,
        startAt: Instant,
        endAt: Instant,
        pickupLocation: String?,
    ): Appointment {
        if (appointmentRepository.overlapsExisting(instructorId, startAt, endAt)) throw SlotConflictException(instructorId)
        val appointment =
            appointmentRepository.save(
                Appointment(
                    AppointmentId(UUID.randomUUID()),
                    studentId,
                    instructorId,
                    null,
                    startAt,
                    endAt,
                    pickupLocation,
                ),
            )
        eventPublisher.publish(
            EventEnvelope(
                eventType = "appointment.created",
                source = "appointment-service",
                payload = AppointmentCreatedPayload(appointment.id.value, studentId, instructorId, startAt),
            ),
        )
        return appointment
    }
}

@ApplicationScoped
class RescheduleAppointmentUseCase(
    private val appointmentRepository: AppointmentRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        id: AppointmentId,
        newStart: Instant,
        newEnd: Instant,
    ): Appointment {
        val existing = appointmentRepository.findByAppointmentId(id) ?: throw AppointmentNotFoundException(id)
        if (appointmentRepository.overlapsExisting(existing.instructorId, newStart, newEnd, existing.id)) {
            throw SlotConflictException(existing.instructorId)
        }
        val updated =
            appointmentRepository.save(
                existing.copy(startAt = newStart, endAt = newEnd, status = AppointmentStatus.RESCHEDULED),
            )
        eventPublisher.publish(
            EventEnvelope(
                eventType = "appointment.rescheduled",
                source = "appointment-service",
                payload = mapOf("appointmentId" to id.value, "oldStart" to existing.startAt, "newStart" to newStart),
            ),
        )
        return updated
    }
}

@ApplicationScoped
class CancelAppointmentUseCase(
    private val appointmentRepository: AppointmentRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(id: AppointmentId) {
        val existing = appointmentRepository.findByAppointmentId(id) ?: throw AppointmentNotFoundException(id)
        appointmentRepository.updateStatus(id, AppointmentStatus.CANCELLED)
        eventPublisher.publish(
            EventEnvelope(
                eventType = "appointment.cancelled",
                source = "appointment-service",
                payload = mapOf("appointmentId" to id.value, "studentId" to existing.studentId),
            ),
        )
    }
}

@ApplicationScoped
class CompleteAppointmentUseCase(
    private val repo: AppointmentRepository,
) {
    @Transactional
    fun execute(id: AppointmentId) {
        repo.updateStatus(id, AppointmentStatus.COMPLETED)
    }
}

@ApplicationScoped
class ListAppointmentsUseCase(
    private val repo: AppointmentRepository,
) {
    fun execute(
        studentId: UUID?,
        instructorId: UUID?,
        from: Instant?,
        to: Instant?,
    ) = repo.list(studentId, instructorId, from, to)
}
