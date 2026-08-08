package org.rnair.driveplusplus.userservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.UserRole
import java.time.Instant

@Entity
@Table(name = "user_accounts")
class UserAccountJpaEntity : BaseJpaEntity() {
    @Column(name = "email")
    lateinit var email: String

    @Column(name = "password_hash")
    lateinit var passwordHash: String

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    lateinit var role: UserRole

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: AccountStatus = AccountStatus.ACTIVE

    @Column(name = "created_at")
    lateinit var createdAt: Instant
}
