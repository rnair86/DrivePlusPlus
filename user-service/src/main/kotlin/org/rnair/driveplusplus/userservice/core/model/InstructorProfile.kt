package org.rnair.driveplusplus.userservice.core.model

data class InstructorProfile(
    val userId: UserId,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val licenseCertNumber: String?,
    val yearsExperience: Int = 0,
    val specialties: List<String> = emptyList(),
    val vehicleAssigned: String?,
)
