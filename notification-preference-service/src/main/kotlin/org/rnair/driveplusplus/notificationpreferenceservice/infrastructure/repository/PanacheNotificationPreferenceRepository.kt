package org.rnair.driveplusplus.notificationpreferenceservice.infrastructure.repository

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.notificationpreferenceservice.core.models.NotificationPreference
import org.rnair.driveplusplus.notificationpreferenceservice.core.repository.NotificationPreferenceRepository
import org.rnair.driveplusplus.notificationpreferenceservice.infrastructure.entities.NotificationPreferenceJpaEntity
import org.rnair.driveplusplus.notificationpreferenceservice.infrastructure.entities.NotificationPreferenceKey
import org.rnair.driveplusplus.notificationpreferenceservice.infrastructure.mappers.NotificationPreferenceMapper
import java.util.UUID

@ApplicationScoped
class PanacheNotificationPreferenceRepository :
    NotificationPreferenceRepository,
    PanacheRepositoryBase<NotificationPreferenceJpaEntity, NotificationPreferenceKey> {
    override fun upsert(preference: NotificationPreference): NotificationPreference {
        val existing = findOneEntity(preference.userId, preference.eventType, preference.channel)
        val entity = existing ?: NotificationPreferenceMapper.toEntity(preference)
        entity.enabled = preference.enabled
        entity.quietHoursStart = preference.quietHoursStart
        entity.quietHoursEnd = preference.quietHoursEnd
        if (existing == null) persist(entity)
        return NotificationPreferenceMapper.toDomain(entity)
    }

    override fun findAllForUser(userId: UUID) = find("userId", userId).list().map(NotificationPreferenceMapper::toDomain)

    override fun findOne(
        userId: UUID,
        eventType: NotificationEventType,
        channel: NotificationChannel,
    ) = findOneEntity(userId, eventType, channel)?.let(NotificationPreferenceMapper::toDomain)

    private fun findOneEntity(
        userId: UUID,
        eventType: NotificationEventType,
        channel: NotificationChannel,
    ) = find(
        "userId = ?1 and eventType = ?2 and channel = ?3",
        userId,
        eventType,
        channel,
    ).firstResult()
}
