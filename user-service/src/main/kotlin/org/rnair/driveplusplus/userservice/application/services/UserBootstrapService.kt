package org.rnair.driveplusplus.userservice.application.services

import io.quarkus.logging.Log
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.userservice.core.model.AccountStatus
import org.rnair.driveplusplus.userservice.core.model.UserAccount
import org.rnair.driveplusplus.userservice.core.model.UserId
import org.rnair.driveplusplus.userservice.core.model.UserRole
import org.rnair.driveplusplus.userservice.core.repository.PasswordHasher
import org.rnair.driveplusplus.userservice.core.repository.UserAccountRepository
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class UserBootstrapService(
    private val userAccountRepository: UserAccountRepository,
    private val passwordHasher: PasswordHasher,
) {
    // Todo: Inject Defaultpassword from settings for admin

    @Transactional
    fun onStart(
        @Observes e: StartupEvent,
    ) {
        if (userAccountRepository.getCount() == 0L) {
            // Initialize default  admin user
            userAccountRepository.save(
                UserAccount(
                    UserId(UUID.randomUUID()),
                    "admin@driveplusplus.com",
                    passwordHasher.hash("testPassword"),
                    UserRole.ADMIN,
                    AccountStatus.ACTIVE,
                    Instant.now(),
                ),
            )
            Log.info("Default admin user created")
        }
    }
}
