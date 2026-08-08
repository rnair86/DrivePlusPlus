package org.rnair.driveplusplus.userservice.infrastructure.mappers

import org.rnair.driveplusplus.userservice.core.model.UserAccount
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.infrastructure.entities.UserAccountJpaEntity

object UserAccountMapper {
    fun toDomain(e: UserAccountJpaEntity) = UserAccount(UserId(e.id), e.email, e.passwordHash, e.role, e.status, e.createdAt)

    fun toEntity(a: UserAccount) =
        UserAccountJpaEntity().apply {
            id = a.id.value
            email = a.email
            passwordHash = a.passwordHash
            role = a.role
            status = a.status
            createdAt = a.createdAt
        }
}
