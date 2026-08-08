package org.rnair.driveplusplus.userservice.web.dtos

import java.util.UUID

data class StudentResponse(
    val userId: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val licenseStage: String,
)
