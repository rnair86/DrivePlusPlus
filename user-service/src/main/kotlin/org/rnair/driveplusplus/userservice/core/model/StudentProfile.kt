package org.rnair.driveplusplus.userservice.core.model

import java.time.LocalDate
import java.time.Period

data class StudentProfile(
    val userId: UserId,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val dateOfBirth: LocalDate?,
    val guardianContact: String?,
    val licenseStage: LicenseStage = LicenseStage.LEARNER,
) {
    fun isMinor(): Boolean = dateOfBirth != null && Period.between(dateOfBirth, LocalDate.now()).years < 18
}
