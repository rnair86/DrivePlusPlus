package org.rnair.driveplusplus.notificationservice.core.repository

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import org.rnair.driveplusplus.notificationservice.core.models.NotificationLogEntry
import org.rnair.driveplusplus.notificationservice.core.models.NotificationTemplate
import org.rnair.driveplusplus.notificationservice.core.models.PreferenceCacheEntry
import org.rnair.driveplusplus.notificationservice.core.models.SendResult
import java.time.Instant
import java.util.UUID

interface NotificationLogRepository {
    fun existsFor(
        sourceEventId: UUID,
        userId: UUID,
        channel: NotificationChannel,
    ): Boolean

    fun save(entry: NotificationLogEntry): NotificationLogEntry

    fun history(
        userId: UUID?,
        eventType: NotificationEventType?,
        from: Instant?,
        to: Instant?,
    ): List<NotificationLogEntry>
}

interface PreferenceCacheRepository {
    fun isEnabled(
        userId: UUID,
        eventType: NotificationEventType,
        channel: NotificationChannel,
    ): Boolean

    fun upsert(entry: PreferenceCacheEntry)
}

interface TemplateRepository {
    fun find(
        eventType: NotificationEventType,
        channel: NotificationChannel,
    ): NotificationTemplate?

    fun save(template: NotificationTemplate): NotificationTemplate

    fun list(): List<NotificationTemplate>
}

interface NotificationSender {
    fun send(
        channel: NotificationChannel,
        recipient: String,
        subject: String?,
        body: String,
    ): SendResult
}
