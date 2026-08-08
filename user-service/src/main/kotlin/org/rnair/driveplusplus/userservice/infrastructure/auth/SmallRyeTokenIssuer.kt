package org.rnair.driveplusplus.userservice.infrastructure.auth

import io.smallrye.jwt.build.Jwt
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.model.UserRole
import org.rnair.driveplusplus.userservice.core.repository.TokenIssuer
import java.time.Duration

@ApplicationScoped
class SmallRyeTokenIssuer : TokenIssuer {
    override fun issue(
        userId: UserId,
        role: UserRole,
    ): String =
        Jwt
            .issuer("identity-service")
            .subject(userId.value.toString())
            .groups(setOf(role.name))
            .expiresIn(Duration.ofHours(8))
            .sign()
}
