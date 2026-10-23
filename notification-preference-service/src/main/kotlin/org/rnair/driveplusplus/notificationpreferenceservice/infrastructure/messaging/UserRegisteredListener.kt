package org.rnair.driveplusplus.notificationpreferenceservice.infrastructure.messaging

import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.rnair.driveplusplus.notificationpreferenceservice.application.usecase.SeedDefaultPreferencesUseCase
import java.time.Instant
import java.util.UUID

data class UserRegisteredPayload(
    val studentId: UUID? = null,
    val instructorId: UUID? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)

data class UserRegisteredEnvelope(
    val eventId: UUID,
    val eventType: String,
    val occurredAt: Instant,
    val version: String,
    val source: String,
    val correlationId: String? = null,
    val payload: UserRegisteredPayload,
)

@ApplicationScoped
class UserRegisteredListener(
    private val seedDefaultPreferencesUseCase: SeedDefaultPreferencesUseCase,
) {
    @Incoming("user-events")
    @RunOnVirtualThread
    fun onMessage(envelope: UserRegisteredEnvelope) {
        when (envelope.eventType) {
            "user.student.registered" -> {
                envelope.payload.studentId?.let { seedDefaultPreferencesUseCase.execute(it, "STUDENT") }
            }

            "user.instructor.registered" -> {
                envelope.payload.instructorId?.let { seedDefaultPreferencesUseCase.execute(it, "INSTRUCTOR") }
            }
        }
    }
}
