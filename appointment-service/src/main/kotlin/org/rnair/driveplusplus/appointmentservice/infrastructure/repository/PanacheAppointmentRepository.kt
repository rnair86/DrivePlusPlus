package org.rnair.driveplusplus.appointmentservice.infrastructure.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.appointmentservice.core.models.Appointment
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentId
import org.rnair.driveplusplus.appointmentservice.core.models.AppointmentStatus
import org.rnair.driveplusplus.appointmentservice.core.repository.AppointmentRepository
import org.rnair.driveplusplus.appointmentservice.infrastructure.entities.AppointmentJpaEntity
import org.rnair.driveplusplus.appointmentservice.infrastructure.mappers.AppointmentMapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ApplicationScoped
class PanacheAppointmentRepository :
    AppointmentRepository,
    PanacheRepositoryBase<AppointmentJpaEntity, UUID> {
    override fun save(appointment: Appointment): Appointment {
        val entity = AppointmentMapper.toEntity(appointment)
        val managed =
            if (findById(entity.id) == null) {
                persist(entity)
                entity
            } else {
                getEntityManager().merge(entity)
            }
        return AppointmentMapper.toDomain(managed)
    }

    override fun findByAppointmentId(id: AppointmentId) = findById(id.value)?.let(AppointmentMapper::toDomain)

    override fun overlapsExisting(
        instructorId: UUID,
        startAt: Instant,
        endAt: Instant,
        excludeId: AppointmentId?,
    ): Boolean {
        val clauses =
            mutableListOf(
                "instructorId = ?1",
                "status != ?2",
                "startAt < ?3",
                "endAt > ?4",
            )
        val params = mutableListOf<Any>(instructorId, AppointmentStatus.CANCELLED, endAt, startAt)
        if (excludeId != null) {
            clauses += "id != ?5"
            params += excludeId.value
        }
        return count(clauses.joinToString(" and "), *params.toTypedArray()) > 0
    }

    override fun list(
        studentId: UUID?,
        instructorId: UUID?,
        from: Instant?,
        to: Instant?,
    ): List<Appointment> {
        val clauses = mutableListOf<String>()
        val params = mutableListOf<Any>()
        if (studentId != null) {
            clauses += "studentId = ?${params.size + 1}"
            params += studentId
        }
        if (instructorId != null) {
            clauses += "instructorId = ?${params.size + 1}"
            params += instructorId
        }
        if (from != null) {
            clauses += "startAt >= ?${params.size + 1}"
            params += from
        }
        if (to != null) {
            clauses += "startAt <= ?${params.size + 1}"
            params += to
        }
        val query = if (clauses.isEmpty()) findAll() else find(clauses.joinToString(" and "), *params.toTypedArray())
        return query.list().map(AppointmentMapper::toDomain)
    }

    override fun findStartingWithinWithNoReminderSent(withinHours: Long): List<Appointment> {
        val cutoff = Instant.now().plus(withinHours, ChronoUnit.HOURS)
        return find("startAt <= ?1 and reminderSentAt is null and status = ?2", cutoff, AppointmentStatus.CONFIRMED)
            .list()
            .map(AppointmentMapper::toDomain)
    }

    override fun markReminderSent(id: AppointmentId) {
        update("reminderSentAt = ?1 where id = ?2", Instant.now(), id.value)
    }

    override fun updateStatus(
        id: AppointmentId,
        status: AppointmentStatus,
    ) {
        update("status = ?1 where id = ?2", status, id.value)
    }
}
