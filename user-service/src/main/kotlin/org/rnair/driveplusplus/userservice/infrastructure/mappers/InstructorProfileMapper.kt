package org.rnair.driveplusplus.userservice.infrastructure.mappers

import org.rnair.driveplusplus.userservice.core.model.InstructorProfile
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.infrastructure.entities.InstructorProfileJpaEntity

object InstructorProfileMapper {
    fun toDomain(e: InstructorProfileJpaEntity) =
        InstructorProfile(
            UserId(e.userId),
            e.firstName,
            e.lastName,
            e.phone,
            e.licenseCertNumber,
            e.yearsExperience,
            e.specialties.toList(),
            e.vehicleAssigned,
        )

    fun toEntity(p: InstructorProfile) =
        InstructorProfileJpaEntity().apply {
            userId = p.userId.value
            firstName = p.firstName
            lastName = p.lastName
            phone = p.phone
            licenseCertNumber = p.licenseCertNumber
            yearsExperience = p.yearsExperience
            specialties = p.specialties.toMutableList()
            vehicleAssigned = p.vehicleAssigned
        }
}
