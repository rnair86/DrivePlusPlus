package org.rnair.driveplusplus.userservice.infrastructure.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.UserAccount
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.repository.UserAccountRepository
import org.rnair.driveplusplus.userservice.infrastructure.entities.UserAccountJpaEntity
import org.rnair.driveplusplus.userservice.infrastructure.mappers.UserAccountMapper
import java.util.UUID

@ApplicationScoped
class PanacheUserAccountRepository :
    UserAccountRepository,
    PanacheRepositoryBase<UserAccountJpaEntity, UUID> {
    override fun save(account: UserAccount): UserAccount {
        val e = UserAccountMapper.toEntity(account)
        persist(e)
        return UserAccountMapper.toDomain(e)
    }

    override fun findById(id: UserId) = findById(id.value)?.let(UserAccountMapper::toDomain)

    override fun findByEmail(email: String) =
        find("email", email)
            .firstResult()
            ?.let(UserAccountMapper::toDomain)

    override fun existsByEmail(email: String) = count("email", email) > 0

    override fun updateStatus(
        id: UserId,
        status: AccountStatus,
    ) {
        update("status = ?1 where id = ?2", status, id.value)
    }

    override fun getCount(): Long = count()
}
