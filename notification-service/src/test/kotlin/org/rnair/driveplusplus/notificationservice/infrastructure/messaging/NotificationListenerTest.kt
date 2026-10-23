package org.rnair.driveplusplus.notificationservice.infrastructure.messaging

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.notificationservice.application.usecase.ProcessDomainEventUseCase
import org.rnair.driveplusplus.notificationservice.application.usecase.UpdatePreferenceCacheUseCase
import org.rnair.driveplusplus.notificationservice.core.models.Recipient
import java.time.Instant
import java.util.UUID

class NotificationListenerTest {
    @Test
    fun forwardsAMappedDomainEvent() {
        val process = mockk<ProcessDomainEventUseCase>()
        val eventId = UUID.fromString("00000000-0000-0000-0000-00000000d101")
        val studentId = UUID.fromString("00000000-0000-0000-0000-00000000d102")
        every { process.execute(any(), any(), any()) } just runs
        val payload =
            JsonNodeFactory.instance
                .objectNode()
                .put("studentId", studentId.toString())
                .put("email", "student@example.com")

        DomainEventListener(process).onUserEvent(domainEnvelope(eventId, "user.student.registered", payload))

        verify(exactly = 1) {
            process.execute(eventId, NotificationEventType.STUDENT_REGISTERED, listOf(Recipient(studentId, "student@example.com")))
        }
    }

    @Test
    fun ignoresAnUnmappedDomainEvent() {
        val process = mockk<ProcessDomainEventUseCase>()
        every { process.execute(any(), any(), any()) } just runs

        DomainEventListener(process).onClassEvent(
            domainEnvelope(UUID.randomUUID(), "class.session.scheduled", JsonNodeFactory.instance.objectNode()),
        )

        verify(exactly = 0) { process.execute(any(), any(), any()) }
    }

    @Test
    fun forwardsPreferenceUpdatesAndIgnoresUnknownNames() {
        val update = mockk<UpdatePreferenceCacheUseCase>()
        val userId = UUID.fromString("00000000-0000-0000-0000-00000000d103")
        every { update.execute(any(), any(), any(), any()) } just runs
        val listener = PreferenceUpdatedListener(update)

        listener.onMessage(preferenceEnvelope(userId, "APPOINTMENT_CREATED", "EMAIL", false))
        listener.onMessage(preferenceEnvelope(userId, "NOT_A_TYPE", "EMAIL", true))

        verify(exactly = 1) {
            update.execute(userId, NotificationEventType.APPOINTMENT_CREATED, NotificationChannel.EMAIL, false)
        }
    }

    private fun domainEnvelope(
        eventId: UUID,
        eventType: String,
        payload: com.fasterxml.jackson.databind.JsonNode,
    ) = DomainEventEnvelope(
        eventId = eventId,
        eventType = eventType,
        occurredAt = Instant.parse("2026-10-22T12:00:00Z"),
        version = "1.0",
        source = "user-service",
        payload = payload,
    )

    private fun preferenceEnvelope(
        userId: UUID,
        eventType: String,
        channel: String,
        enabled: Boolean,
    ) = PreferenceUpdatedEnvelope(
        eventId = UUID.randomUUID(),
        eventType = "preference.updated",
        occurredAt = Instant.parse("2026-10-22T12:00:00Z"),
        version = "1.0",
        source = "notification-preference-service",
        payload = PreferenceUpdatedPayload(userId, eventType, channel, enabled),
    )
}
