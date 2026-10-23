package org.rnair.driveplusplus.notificationservice.web.dtos

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import java.time.Instant
import java.util.UUID

data class NotificationLogResponse(
    val id: UUID,
    val userId: UUID,
    val sourceEventId: UUID,
    val eventType: String,
    val channel: String,
    val status: String,
    val providerMessageId: String?,
    val sentAt: Instant,
    val errorMessage: String?,
)

data class TemplateRequest(
    val eventType: NotificationEventType,
    val channel: NotificationChannel,
    val subjectTemplate: String?,
    val bodyTemplate: String,
)

data class TemplateResponse(
    val id: UUID,
    val eventType: String,
    val channel: String,
    val subjectTemplate: String?,
    val bodyTemplate: String,
)
