package org.rnair.driveplusplus.notificationpreferenceservice.infrastructure.mappers

import org.rnair.driveplusplus.notificationpreferenceservice.core.models.NotificationPreference
import org.rnair.driveplusplus.notificationpreferenceservice.infrastructure.entities.NotificationPreferenceJpaEntity

object NotificationPreferenceMapper {
    fun toDomain(entity: NotificationPreferenceJpaEntity) =
        NotificationPreference(
            userId = entity.userId,
            eventType = entity.eventType,
            channel = entity.channel,
            enabled = entity.enabled,
            quietHoursStart = entity.quietHoursStart,
            quietHoursEnd = entity.quietHoursEnd,
        )

    fun toEntity(preference: NotificationPreference) =
        NotificationPreferenceJpaEntity().apply {
            userId = preference.userId
            eventType = preference.eventType
            channel = preference.channel
            enabled = preference.enabled
            quietHoursStart = preference.quietHoursStart
            quietHoursEnd = preference.quietHoursEnd
        }
}
