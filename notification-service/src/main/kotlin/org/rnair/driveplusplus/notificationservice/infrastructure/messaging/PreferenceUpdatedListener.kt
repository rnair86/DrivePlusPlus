package org.rnair.driveplusplus.notificationservice.infrastructure.messaging

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.rnair.driveplusplus.notificationservice.application.usecase.UpdatePreferenceCacheUseCase
import java.time.Instant
import java.util.UUID

data class PreferenceUpdatedPayload(
    val userId: UUID,
    val eventType: String,
    val channel: String,
    val enabled: Boolean,
)

data class PreferenceUpdatedEnvelope(
    val eventId: UUID,
    val eventType: String,
    val occurredAt: Instant,
    val version: String,
    val source: String,
    val correlationId: String? = null,
    val payload: PreferenceUpdatedPayload,
)

@ApplicationScoped
class PreferenceUpdatedListener(
    private val updatePreferenceCacheUseCase: UpdatePreferenceCacheUseCase,
) {
    @Incoming("preference-updates")
    @RunOnVirtualThread
    fun onMessage(envelope: PreferenceUpdatedEnvelope) {
        val eventType = runCatching { NotificationEventType.valueOf(envelope.payload.eventType) }.getOrNull() ?: return
        val channel = runCatching { NotificationChannel.valueOf(envelope.payload.channel) }.getOrNull() ?: return
        updatePreferenceCacheUseCase.execute(envelope.payload.userId, eventType, channel, envelope.payload.enabled)
    }
}
