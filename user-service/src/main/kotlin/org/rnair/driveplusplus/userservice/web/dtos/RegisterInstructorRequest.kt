package org.rnair.driveplusplus.userservice.web.dtos

data class RegisterInstructorRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val licenseCertNumber: String?,
    val yearsExperience: Int,
    val specialties: List<String>,
    val vehicleAssigned: String?,
)
