package org.rnair.driveplusplus.userservice.web.dtos

import java.time.LocalDate

data class RegisterStudentRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val dateOfBirth: LocalDate?,
    val guardianContact: String?,
)
