package org.rnair.driveplusplus.userservice.infrastructure.auth

import io.quarkus.elytron.security.common.BcryptUtil
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.userservice.core.repository.PasswordHasher

@ApplicationScoped
class BCryptPasswordHasher : PasswordHasher {
    override fun hash(rawPassword: String): String = BcryptUtil.bcryptHash(rawPassword)

    override fun matches(
        rawPassword: String,
        hash: String,
    ): Boolean = BcryptUtil.matches(rawPassword, hash)
}
