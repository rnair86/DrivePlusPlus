package org.rnair.driveplusplus.userservice.web.dtos

data class LoginRequest(
    val email: String,
    val password: String,
)
