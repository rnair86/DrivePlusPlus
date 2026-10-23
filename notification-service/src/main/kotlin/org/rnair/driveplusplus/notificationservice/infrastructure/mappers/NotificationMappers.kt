package org.rnair.driveplusplus.notificationservice.infrastructure.mappers

import org.rnair.driveplusplus.notificationservice.core.models.NotificationLogEntry
import org.rnair.driveplusplus.notificationservice.core.models.NotificationTemplate
import org.rnair.driveplusplus.notificationservice.infrastructure.entities.NotificationLogJpaEntity
import org.rnair.driveplusplus.notificationservice.infrastructure.entities.TemplateJpaEntity

object NotificationLogMapper {
    fun toDomain(entity: NotificationLogJpaEntity) =
        NotificationLogEntry(
            id = entity.id,
            userId = entity.userId,
            sourceEventId = entity.sourceEventId,
            eventType = entity.eventType,
            channel = entity.channel,
            status = entity.status,
            providerMessageId = entity.providerMessageId,
            sentAt = entity.sentAt,
            errorMessage = entity.errorMessage,
        )

    fun toEntity(entry: NotificationLogEntry) =
        NotificationLogJpaEntity().apply {
            id = entry.id
            userId = entry.userId
            sourceEventId = entry.sourceEventId
            eventType = entry.eventType
            channel = entry.channel
            status = entry.status
            providerMessageId = entry.providerMessageId
            sentAt = entry.sentAt
            errorMessage = entry.errorMessage
        }
}

object TemplateMapper {
    fun toDomain(entity: TemplateJpaEntity) =
        NotificationTemplate(
            id = entity.id,
            eventType = entity.eventType,
            channel = entity.channel,
            subjectTemplate = entity.subjectTemplate,
            bodyTemplate = entity.bodyTemplate,
        )

    fun toEntity(template: NotificationTemplate) =
        TemplateJpaEntity().apply {
            id = template.id
            eventType = template.eventType
            channel = template.channel
            subjectTemplate = template.subjectTemplate
            bodyTemplate = template.bodyTemplate
        }
}
