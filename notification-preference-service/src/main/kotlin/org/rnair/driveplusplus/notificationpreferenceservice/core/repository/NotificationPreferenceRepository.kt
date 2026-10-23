package org.rnair.driveplusplus.notificationpreferenceservice.core.repository

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import org.rnair.driveplusplus.notificationpreferenceservice.core.models.NotificationPreference
import java.util.UUID

interface NotificationPreferenceRepository {
    fun upsert(preference: NotificationPreference): NotificationPreference

    fun findAllForUser(userId: UUID): List<NotificationPreference>

    fun findOne(
        userId: UUID,
        eventType: NotificationEventType,
        channel: NotificationChannel,
    ): NotificationPreference?
}
