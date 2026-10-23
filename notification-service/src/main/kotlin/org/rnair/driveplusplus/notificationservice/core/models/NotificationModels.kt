package org.rnair.driveplusplus.notificationservice.core.models

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import java.time.Instant
import java.util.UUID

enum class DeliveryStatus { SENT, FAILED, SUPPRESSED_BY_PREFERENCE }

data class Recipient(
    val userId: UUID,
    val address: String,
)

data class NotificationLogEntry(
    val id: UUID,
    val userId: UUID,
    val sourceEventId: UUID,
    val eventType: NotificationEventType,
    val channel: NotificationChannel,
    val status: DeliveryStatus,
    val providerMessageId: String?,
    val sentAt: Instant,
    val errorMessage: String?,
)

data class PreferenceCacheEntry(
    val userId: UUID,
    val eventType: NotificationEventType,
    val channel: NotificationChannel,
    val enabled: Boolean,
)

data class NotificationTemplate(
    val id: UUID,
    val eventType: NotificationEventType,
    val channel: NotificationChannel,
    val subjectTemplate: String?,
    val bodyTemplate: String,
) {
    fun render(recipient: Recipient): String = bodyTemplate.replace("{{recipient}}", recipient.address)
}

data class SendResult(
    val success: Boolean,
    val providerMessageId: String?,
    val error: String?,
)
