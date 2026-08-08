package org.rnair.driveplusplus.userservice.core.repository

interface PasswordHasher {
    fun hash(rawPassword: String): String

    fun matches(
        rawPassword: String,
        hash: String,
    ): Boolean
}
