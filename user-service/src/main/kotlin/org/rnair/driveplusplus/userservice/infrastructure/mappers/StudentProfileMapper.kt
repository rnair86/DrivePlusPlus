package org.rnair.driveplusplus.userservice.infrastructure.mappers

import org.rnair.driveplusplus.userservice.core.model.StudentProfile
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.infrastructure.entities.StudentProfileJpaEntity

object StudentProfileMapper {
    fun toDomain(e: StudentProfileJpaEntity) =
        StudentProfile(
            UserId(e.userId),
            e.firstName,
            e.lastName,
            e.phone,
            e.dateOfBirth,
            e.guardianContact,
            e.licenseStage,
        )

    fun toEntity(p: StudentProfile) =
        StudentProfileJpaEntity().apply {
            userId = p.userId.value
            firstName = p.firstName
            lastName = p.lastName
            phone = p.phone
            dateOfBirth = p.dateOfBirth
            guardianContact = p.guardianContact
            licenseStage = p.licenseStage
        }
}
