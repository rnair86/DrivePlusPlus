package org.rnair.driveplusplus.userservice.core.repository

import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.model.UserRole

interface TokenIssuer {
    fun issue(
        userId: UserId,
        role: UserRole,
    ): String
}
