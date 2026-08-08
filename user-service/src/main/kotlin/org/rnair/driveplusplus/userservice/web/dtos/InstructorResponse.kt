package org.rnair.driveplusplus.userservice.web.dtos

import java.util.UUID

data class InstructorResponse(
    val userId: UUID,
    val firstName: String,
    val lastName: String,
    val specialties: List<String>,
)
