package org.rnair.driveplusplus.notificationpreferenceservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.notificationpreferenceservice.core.models.NotificationPreference
import org.rnair.driveplusplus.notificationpreferenceservice.core.repository.NotificationPreferenceRepository
import java.util.UUID

data class PreferenceUpdatedPayload(
    val userId: UUID,
    val eventType: String,
    val channel: String,
    val enabled: Boolean,
)

@ApplicationScoped
class UpsertPreferencesUseCase(
    private val repository: NotificationPreferenceRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        userId: UUID,
        preferences: List<NotificationPreference>,
    ): List<NotificationPreference> =
        preferences.map { preference ->
            val saved = repository.upsert(preference.copy(userId = userId))
            publish(saved)
            saved
        }

    private fun publish(saved: NotificationPreference) {
        eventPublisher.publish(
            EventEnvelope(
                eventType = "preference.updated",
                source = "notification-preference-service",
                payload =
                    PreferenceUpdatedPayload(
                        saved.userId,
                        saved.eventType.name,
                        saved.channel.name,
                        saved.enabled,
                    ),
            ),
        )
    }
}

@ApplicationScoped
class GetPreferenceMatrixUseCase(
    private val repository: NotificationPreferenceRepository,
) {
    fun execute(userId: UUID): List<NotificationPreference> = repository.findAllForUser(userId)
}

@ApplicationScoped
class TogglePreferenceUseCase(
    private val repository: NotificationPreferenceRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        userId: UUID,
        eventType: NotificationEventType,
        channel: NotificationChannel,
        enabled: Boolean,
    ): NotificationPreference {
        val existing = repository.findOne(userId, eventType, channel)
        val saved =
            repository.upsert(
                NotificationPreference(
                    userId = userId,
                    eventType = eventType,
                    channel = channel,
                    enabled = enabled,
                    quietHoursStart = existing?.quietHoursStart,
                    quietHoursEnd = existing?.quietHoursEnd,
                ),
            )
        eventPublisher.publish(
            EventEnvelope(
                eventType = "preference.updated",
                source = "notification-preference-service",
                payload = PreferenceUpdatedPayload(userId, eventType.name, channel.name, enabled),
            ),
        )
        return saved
    }
}

@ApplicationScoped
class SeedDefaultPreferencesUseCase(
    private val repository: NotificationPreferenceRepository,
) {
    @Transactional
    fun execute(
        userId: UUID,
        role: String,
    ) {
        val events = if (role == "STUDENT") studentEvents else instructorEvents
        events.forEach { eventType ->
            repository.upsert(
                NotificationPreference(
                    userId = userId,
                    eventType = eventType,
                    channel = NotificationChannel.EMAIL,
                    enabled = true,
                ),
            )
        }
    }

    private val studentEvents =
        listOf(
            NotificationEventType.APPOINTMENT_CREATED,
            NotificationEventType.APPOINTMENT_REMINDER,
            NotificationEventType.TEST_SCHEDULED,
            NotificationEventType.TEST_REMINDER,
            NotificationEventType.TEST_RESULT_PUBLISHED,
            NotificationEventType.PAYMENT_DUE,
            NotificationEventType.PAYMENT_OVERDUE,
        )

    private val instructorEvents =
        listOf(
            NotificationEventType.APPOINTMENT_CREATED,
            NotificationEventType.CLASS_SESSION_SCHEDULED,
        )
}
