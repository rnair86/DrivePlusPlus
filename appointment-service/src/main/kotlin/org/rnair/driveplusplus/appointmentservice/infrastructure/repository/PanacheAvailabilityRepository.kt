package org.rnair.driveplusplus.appointmentservice.infrastructure.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.appointmentservice.core.models.AvailabilitySlot
import org.rnair.driveplusplus.appointmentservice.core.repository.AvailabilityRepository
import org.rnair.driveplusplus.appointmentservice.infrastructure.entities.AvailabilitySlotJpaEntity
import org.rnair.driveplusplus.appointmentservice.infrastructure.mappers.AvailabilitySlotMapper
import java.util.UUID

@ApplicationScoped
class PanacheAvailabilityRepository :
    AvailabilityRepository,
    PanacheRepositoryBase<AvailabilitySlotJpaEntity, UUID> {
    override fun save(slot: AvailabilitySlot): AvailabilitySlot {
        val e = AvailabilitySlotMapper.toEntity(slot)
        persist(e)
        return AvailabilitySlotMapper.toDomain(e)
    }

    override fun findForInstructor(instructorId: UUID) =
        find("instructorId", instructorId).list().map(AvailabilitySlotMapper::toDomain)
}
