package org.rnair.driveplusplus.notificationpreferenceservice.web.dtos

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import java.time.LocalTime
import java.util.UUID

data class UpsertPreferenceRequest(
    val eventType: NotificationEventType,
    val channel: NotificationChannel,
    val enabled: Boolean = true,
    val quietHoursStart: LocalTime? = null,
    val quietHoursEnd: LocalTime? = null,
)

data class UpsertPreferencesRequest(
    val preferences: List<UpsertPreferenceRequest>,
)

data class TogglePreferenceRequest(
    val enabled: Boolean,
)

data class PreferenceResponse(
    val userId: UUID,
    val eventType: String,
    val channel: String,
    val enabled: Boolean,
    val quietHoursStart: LocalTime?,
    val quietHoursEnd: LocalTime?,
)
