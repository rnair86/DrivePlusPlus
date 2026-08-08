package org.rnair.driveplusplus.userservice.core.repository

import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.UserAccount
import org.rnair.driveplusplus.userservice.core.model.UserId

interface UserAccountRepository {
    fun save(account: UserAccount): UserAccount

    fun findById(id: UserId): UserAccount?

    fun findByEmail(email: String): UserAccount?

    fun existsByEmail(email: String): Boolean

    fun updateStatus(
        id: UserId,
        status: AccountStatus,
    )

    fun getCount(): Long
}
