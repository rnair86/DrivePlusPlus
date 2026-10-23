package org.rnair.driveplusplus.notificationservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.notificationservice.core.models.DeliveryStatus
import org.rnair.driveplusplus.notificationservice.core.models.NotificationLogEntry
import org.rnair.driveplusplus.notificationservice.core.models.NotificationTemplate
import org.rnair.driveplusplus.notificationservice.core.models.PreferenceCacheEntry
import org.rnair.driveplusplus.notificationservice.core.models.Recipient
import org.rnair.driveplusplus.notificationservice.core.repository.NotificationLogRepository
import org.rnair.driveplusplus.notificationservice.core.repository.NotificationSender
import org.rnair.driveplusplus.notificationservice.core.repository.PreferenceCacheRepository
import org.rnair.driveplusplus.notificationservice.core.repository.TemplateRepository
import java.time.Instant
import java.util.UUID

data class NotificationOutcomePayload(
    val userId: UUID,
    val eventType: String,
    val channel: String,
    val providerMessageId: String?,
    val error: String?,
)

@ApplicationScoped
class ProcessDomainEventUseCase(
    private val preferenceCacheRepository: PreferenceCacheRepository,
    private val templateRepository: TemplateRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val notificationSender: NotificationSender,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        sourceEventId: UUID,
        eventType: NotificationEventType,
        recipients: List<Recipient>,
    ) {
        val channel = NotificationChannel.EMAIL
        recipients.forEach { recipient ->
            if (notificationLogRepository.existsFor(sourceEventId, recipient.userId, channel)) return@forEach
            if (!preferenceCacheRepository.isEnabled(recipient.userId, eventType, channel)) {
                notificationLogRepository.save(
                    logEntry(recipient, sourceEventId, eventType, channel, DeliveryStatus.SUPPRESSED_BY_PREFERENCE, null, null),
                )
                return@forEach
            }
            val template = templateRepository.find(eventType, channel) ?: return@forEach
            val result =
                notificationSender.send(channel, recipient.address, template.subjectTemplate, template.render(recipient))
            val status = if (result.success) DeliveryStatus.SENT else DeliveryStatus.FAILED
            notificationLogRepository.save(
                logEntry(recipient, sourceEventId, eventType, channel, status, result.providerMessageId, result.error),
            )
            eventPublisher.publish(
                EventEnvelope(
                    eventType = if (result.success) "notification.sent" else "notification.failed",
                    source = "notification-service",
                    payload =
                        NotificationOutcomePayload(
                            recipient.userId,
                            eventType.name,
                            channel.name,
                            result.providerMessageId,
                            result.error,
                        ),
                ),
            )
        }
    }

    private fun logEntry(
        recipient: Recipient,
        sourceEventId: UUID,
        eventType: NotificationEventType,
        channel: NotificationChannel,
        status: DeliveryStatus,
        providerMessageId: String?,
        errorMessage: String?,
    ) = NotificationLogEntry(
        id = UUID.randomUUID(),
        userId = recipient.userId,
        sourceEventId = sourceEventId,
        eventType = eventType,
        channel = channel,
        status = status,
        providerMessageId = providerMessageId,
        sentAt = Instant.now(),
        errorMessage = errorMessage,
    )
}

@ApplicationScoped
class UpdatePreferenceCacheUseCase(
    private val repository: PreferenceCacheRepository,
) {
    @Transactional
    fun execute(
        userId: UUID,
        eventType: NotificationEventType,
        channel: NotificationChannel,
        enabled: Boolean,
    ) {
        repository.upsert(PreferenceCacheEntry(userId, eventType, channel, enabled))
    }
}

@ApplicationScoped
class GetNotificationHistoryUseCase(
    private val repository: NotificationLogRepository,
) {
    fun execute(
        userId: UUID?,
        eventType: NotificationEventType?,
        from: Instant?,
        to: Instant?,
    ) = repository.history(userId, eventType, from, to)
}

@ApplicationScoped
class UpdateTemplateUseCase(
    private val repository: TemplateRepository,
) {
    @Transactional
    fun execute(
        eventType: NotificationEventType,
        channel: NotificationChannel,
        subjectTemplate: String?,
        bodyTemplate: String,
    ): NotificationTemplate = repository.save(NotificationTemplate(UUID.randomUUID(), eventType, channel, subjectTemplate, bodyTemplate))
}

@ApplicationScoped
class ListTemplatesUseCase(
    private val repository: TemplateRepository,
) {
    fun execute(): List<NotificationTemplate> = repository.list()
}
