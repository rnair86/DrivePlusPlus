package org.rnair.driveplusplus.userservice.core.model

import java.time.Instant

data class UserAccount(
    val id: UserId,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val createdAt: Instant,
)
