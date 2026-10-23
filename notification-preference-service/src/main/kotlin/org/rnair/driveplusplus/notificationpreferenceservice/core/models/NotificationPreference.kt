package org.rnair.driveplusplus.notificationpreferenceservice.core.models

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import java.time.LocalTime
import java.util.UUID

data class NotificationPreference(
    val userId: UUID,
    val eventType: NotificationEventType,
    val channel: NotificationChannel,
    val enabled: Boolean = true,
    val quietHoursStart: LocalTime? = null,
    val quietHoursEnd: LocalTime? = null,
)
