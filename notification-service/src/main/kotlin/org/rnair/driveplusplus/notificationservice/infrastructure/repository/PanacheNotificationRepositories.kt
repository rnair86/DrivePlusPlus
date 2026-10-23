package org.rnair.driveplusplus.notificationservice.infrastructure.repository

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.notificationservice.core.models.NotificationLogEntry
import org.rnair.driveplusplus.notificationservice.core.models.NotificationTemplate
import org.rnair.driveplusplus.notificationservice.core.models.PreferenceCacheEntry
import org.rnair.driveplusplus.notificationservice.core.repository.NotificationLogRepository
import org.rnair.driveplusplus.notificationservice.core.repository.PreferenceCacheRepository
import org.rnair.driveplusplus.notificationservice.core.repository.TemplateRepository
import org.rnair.driveplusplus.notificationservice.infrastructure.entities.NotificationLogJpaEntity
import org.rnair.driveplusplus.notificationservice.infrastructure.entities.PreferenceCacheJpaEntity
import org.rnair.driveplusplus.notificationservice.infrastructure.entities.PreferenceCacheKey
import org.rnair.driveplusplus.notificationservice.infrastructure.entities.TemplateJpaEntity
import org.rnair.driveplusplus.notificationservice.infrastructure.mappers.NotificationLogMapper
import org.rnair.driveplusplus.notificationservice.infrastructure.mappers.TemplateMapper
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class PanacheNotificationLogRepository :
    NotificationLogRepository,
    PanacheRepositoryBase<NotificationLogJpaEntity, UUID> {
    override fun existsFor(
        sourceEventId: UUID,
        userId: UUID,
        channel: NotificationChannel,
    ) = count(
        "sourceEventId = ?1 and userId = ?2 and channel = ?3",
        sourceEventId,
        userId,
        channel,
    ) > 0

    override fun save(entry: NotificationLogEntry): NotificationLogEntry {
        val entity = NotificationLogMapper.toEntity(entry)
        persist(entity)
        return NotificationLogMapper.toDomain(entity)
    }

    override fun history(
        userId: UUID?,
        eventType: NotificationEventType?,
        from: Instant?,
        to: Instant?,
    ): List<NotificationLogEntry> {
        val parts = mutableListOf<String>()
        val params = mutableListOf<Any>()

        fun bind(
            expression: String,
            value: Any,
        ) {
            parts += expression.replace("#", "?${params.size + 1}")
            params += value
        }
        if (userId != null) bind("userId = #", userId)
        if (eventType != null) bind("eventType = #", eventType)
        if (from != null) bind("sentAt >= #", from)
        if (to != null) bind("sentAt <= #", to)
        val query =
            if (parts.isEmpty()) {
                findAll()
            } else {
                find(parts.joinToString(" and "), *params.toTypedArray())
            }
        return query.list().map(NotificationLogMapper::toDomain)
    }
}

@ApplicationScoped
class PanachePreferenceCacheRepository :
    PreferenceCacheRepository,
    PanacheRepositoryBase<PreferenceCacheJpaEntity, PreferenceCacheKey> {
    override fun isEnabled(
        userId: UUID,
        eventType: NotificationEventType,
        channel: NotificationChannel,
    ): Boolean = findOne(userId, eventType, channel)?.enabled ?: true

    override fun upsert(entry: PreferenceCacheEntry) {
        val existing = findOne(entry.userId, entry.eventType, entry.channel)
        if (existing != null) {
            existing.enabled = entry.enabled
            return
        }
        persist(
            PreferenceCacheJpaEntity().apply {
                userId = entry.userId
                eventType = entry.eventType
                channel = entry.channel
                enabled = entry.enabled
            },
        )
    }

    private fun findOne(
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

@ApplicationScoped
class PanacheTemplateRepository :
    TemplateRepository,
    PanacheRepositoryBase<TemplateJpaEntity, UUID> {
    override fun find(
        eventType: NotificationEventType,
        channel: NotificationChannel,
    ) = find("eventType = ?1 and channel = ?2", eventType, channel).firstResult()?.let(TemplateMapper::toDomain)

    override fun save(template: NotificationTemplate): NotificationTemplate {
        val existing = find("eventType = ?1 and channel = ?2", template.eventType, template.channel).firstResult()
        if (existing == null) {
            val entity = TemplateMapper.toEntity(template)
            persist(entity)
            return TemplateMapper.toDomain(entity)
        }
        existing.subjectTemplate = template.subjectTemplate
        existing.bodyTemplate = template.bodyTemplate
        return TemplateMapper.toDomain(existing)
    }

    override fun list() = findAll().list().map(TemplateMapper::toDomain)
}
