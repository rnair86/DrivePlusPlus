package org.rnair.driveplusplus.notificationservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.notificationservice.core.models.DeliveryStatus
import org.rnair.driveplusplus.notificationservice.core.models.NotificationLogEntry
import org.rnair.driveplusplus.notificationservice.core.models.NotificationTemplate
import org.rnair.driveplusplus.notificationservice.core.models.PreferenceCacheEntry
import org.rnair.driveplusplus.notificationservice.core.models.Recipient
import org.rnair.driveplusplus.notificationservice.core.models.SendResult
import org.rnair.driveplusplus.notificationservice.core.repository.NotificationLogRepository
import org.rnair.driveplusplus.notificationservice.core.repository.NotificationSender
import org.rnair.driveplusplus.notificationservice.core.repository.PreferenceCacheRepository
import org.rnair.driveplusplus.notificationservice.core.repository.TemplateRepository
import java.time.Instant
import java.util.UUID

class NotificationUseCasesTest {
    @Test
    fun sendsEmailAndPublishesSent() {
        val harness = harness(SendResult(true, "provider-1", null))
        val recipient = Recipient(UUID.randomUUID(), "student@example.com")

        harness.process.execute(harness.sourceEventId, NotificationEventType.TEST_SCHEDULED, listOf(recipient))

        val entry = harness.logs.entries.single()
        assertEquals(DeliveryStatus.SENT, entry.status)
        assertEquals("provider-1", entry.providerMessageId)
        assertEquals("Hi student@example.com", entry.let { harness.sender.bodies.single() })
        val payload = harness.published.single().payload as NotificationOutcomePayload
        assertEquals("notification.sent", harness.published.single().eventType)
        assertEquals("provider-1", payload.providerMessageId)
        assertEquals(listOf(NotificationChannel.EMAIL), harness.sender.channels)
    }

    @Test
    fun secondPassForTheSameUserPublishesNothing() {
        val harness = harness(SendResult(true, "provider-1", null))
        val recipient = Recipient(UUID.randomUUID(), "student@example.com")

        harness.process.execute(harness.sourceEventId, NotificationEventType.TEST_SCHEDULED, listOf(recipient))
        harness.process.execute(harness.sourceEventId, NotificationEventType.TEST_SCHEDULED, listOf(recipient))

        assertEquals(1, harness.logs.entries.size)
        assertEquals(1, harness.published.size)
    }

    @Test
    fun disabledPreferenceIsSuppressedAndPublishesNothing() {
        val harness = harness(SendResult(true, "provider-1", null))
        val recipient = Recipient(UUID.randomUUID(), "student@example.com")
        harness.cache.disabled.add(Triple(recipient.userId, NotificationEventType.PAYMENT_DUE, NotificationChannel.EMAIL))

        harness.process.execute(harness.sourceEventId, NotificationEventType.PAYMENT_DUE, listOf(recipient))

        assertEquals(
            DeliveryStatus.SUPPRESSED_BY_PREFERENCE,
            harness.logs.entries
                .single()
                .status,
        )
        assertTrue(harness.published.isEmpty())
        assertTrue(harness.sender.channels.isEmpty())
    }

    @Test
    fun missingTemplateStoresNothing() {
        val harness = harness(SendResult(true, "provider-1", null))
        harness.templates.rows.clear()

        harness.process.execute(harness.sourceEventId, NotificationEventType.TEST_SCHEDULED, listOf(Recipient(UUID.randomUUID(), "a")))

        assertTrue(harness.logs.entries.isEmpty())
        assertTrue(harness.published.isEmpty())
    }

    @Test
    fun failedSendPublishesFailed() {
        val harness = harness(SendResult(false, null, "mailbox full"))
        val recipient = Recipient(UUID.randomUUID(), "student@example.com")

        harness.process.execute(harness.sourceEventId, NotificationEventType.PAYMENT_OVERDUE, listOf(recipient))

        assertEquals(
            DeliveryStatus.FAILED,
            harness.logs.entries
                .single()
                .status,
        )
        assertEquals(
            "mailbox full",
            harness.logs.entries
                .single()
                .errorMessage,
        )
        val payload = harness.published.single().payload as NotificationOutcomePayload
        assertEquals("notification.failed", harness.published.single().eventType)
        assertEquals("mailbox full", payload.error)
    }

    @Test
    fun twoRecipientsBothSend() {
        val harness = harness(SendResult(true, "provider-1", null))
        val student = Recipient(UUID.randomUUID(), "student")
        val instructor = Recipient(UUID.randomUUID(), "instructor")

        harness.process.execute(harness.sourceEventId, NotificationEventType.APPOINTMENT_CREATED, listOf(student, instructor))

        assertEquals(
            setOf(student.userId, instructor.userId),
            harness.logs.entries
                .map { it.userId }
                .toSet(),
        )
        assertEquals(2, harness.published.size)
        assertEquals(listOf(NotificationChannel.EMAIL, NotificationChannel.EMAIL), harness.sender.channels)
    }

    @Test
    fun cacheUpsertChangesIsEnabled() {
        val cache = InMemoryPreferenceCache()
        val userId = UUID.randomUUID()

        assertTrue(cache.isEnabled(userId, NotificationEventType.APPOINTMENT_CREATED, NotificationChannel.EMAIL))
        UpdatePreferenceCacheUseCase(cache).execute(userId, NotificationEventType.APPOINTMENT_CREATED, NotificationChannel.EMAIL, false)

        assertFalse(cache.isEnabled(userId, NotificationEventType.APPOINTMENT_CREATED, NotificationChannel.EMAIL))
    }

    @Test
    fun historyPassesFiltersThrough() {
        val repository = mockk<NotificationLogRepository>()
        val userId = UUID.randomUUID()
        val from = Instant.parse("2026-10-01T00:00:00Z")
        val to = Instant.parse("2026-10-31T00:00:00Z")
        every { repository.history(any(), any(), any(), any()) } returns emptyList()

        GetNotificationHistoryUseCase(repository).execute(userId, NotificationEventType.PAYMENT_DUE, from, to)

        verify(exactly = 1) { repository.history(userId, NotificationEventType.PAYMENT_DUE, from, to) }
    }

    @Test
    fun templateSaveReturnsTheStoredSubjectAndBody() {
        val repository = InMemoryTemplates()
        val saved =
            UpdateTemplateUseCase(repository).execute(
                NotificationEventType.TEST_REMINDER,
                NotificationChannel.EMAIL,
                "Upcoming test",
                "Hi {{recipient}}",
            )

        assertEquals("Upcoming test", saved.subjectTemplate)
        assertEquals("Hi {{recipient}}", saved.bodyTemplate)
        assertEquals(saved, repository.find(NotificationEventType.TEST_REMINDER, NotificationChannel.EMAIL))
    }

    private fun harness(result: SendResult): Harness {
        val logs = InMemoryNotificationLog()
        val cache = InMemoryPreferenceCache()
        val templates = InMemoryTemplates()
        templates.save(
            NotificationTemplate(
                UUID.randomUUID(),
                NotificationEventType.TEST_SCHEDULED,
                NotificationChannel.EMAIL,
                "Test",
                "Hi {{recipient}}",
            ),
        )
        templates.save(
            NotificationTemplate(
                UUID.randomUUID(),
                NotificationEventType.PAYMENT_DUE,
                NotificationChannel.EMAIL,
                "Due",
                "Hi {{recipient}}",
            ),
        )
        templates.save(
            NotificationTemplate(
                UUID.randomUUID(),
                NotificationEventType.PAYMENT_OVERDUE,
                NotificationChannel.EMAIL,
                "Overdue",
                "Hi {{recipient}}",
            ),
        )
        templates.save(
            NotificationTemplate(
                UUID.randomUUID(),
                NotificationEventType.APPOINTMENT_CREATED,
                NotificationChannel.EMAIL,
                "Booked",
                "Hi {{recipient}}",
            ),
        )
        val sender = RecordingSender(result)
        val publisher = mockk<DomainEventPublisher>()
        val published = mutableListOf<EventEnvelope<*>>()
        every { publisher.publish(capture(published)) } just runs
        return Harness(
            ProcessDomainEventUseCase(cache, templates, logs, sender, publisher),
            logs,
            cache,
            templates,
            sender,
            published,
            UUID.randomUUID(),
        )
    }

    private data class Harness(
        val process: ProcessDomainEventUseCase,
        val logs: InMemoryNotificationLog,
        val cache: InMemoryPreferenceCache,
        val templates: InMemoryTemplates,
        val sender: RecordingSender,
        val published: MutableList<EventEnvelope<*>>,
        val sourceEventId: UUID,
    )
}

private class RecordingSender(
    private val result: SendResult,
) : NotificationSender {
    val channels = mutableListOf<NotificationChannel>()
    val bodies = mutableListOf<String>()

    override fun send(
        channel: NotificationChannel,
        recipient: String,
        subject: String?,
        body: String,
    ): SendResult {
        channels += channel
        bodies += body
        return result
    }
}

private class InMemoryNotificationLog : NotificationLogRepository {
    val entries = mutableListOf<NotificationLogEntry>()

    override fun existsFor(
        sourceEventId: UUID,
        userId: UUID,
        channel: NotificationChannel,
    ) = entries.any { it.sourceEventId == sourceEventId && it.userId == userId && it.channel == channel }

    override fun save(entry: NotificationLogEntry): NotificationLogEntry {
        entries += entry
        return entry
    }

    override fun history(
        userId: UUID?,
        eventType: NotificationEventType?,
        from: Instant?,
        to: Instant?,
    ) = entries
}

private class InMemoryPreferenceCache : PreferenceCacheRepository {
    val disabled = mutableSetOf<Triple<UUID, NotificationEventType, NotificationChannel>>()

    override fun isEnabled(
        userId: UUID,
        eventType: NotificationEventType,
        channel: NotificationChannel,
    ) = Triple(userId, eventType, channel) !in disabled

    override fun upsert(entry: PreferenceCacheEntry) {
        val key = Triple(entry.userId, entry.eventType, entry.channel)
        if (entry.enabled) disabled.remove(key) else disabled.add(key)
    }
}

private class InMemoryTemplates : TemplateRepository {
    val rows = linkedMapOf<Pair<NotificationEventType, NotificationChannel>, NotificationTemplate>()

    override fun find(
        eventType: NotificationEventType,
        channel: NotificationChannel,
    ) = rows[eventType to channel]

    override fun save(template: NotificationTemplate): NotificationTemplate {
        val existing = rows[template.eventType to template.channel]
        val stored = existing?.copy(subjectTemplate = template.subjectTemplate, bodyTemplate = template.bodyTemplate) ?: template
        rows[template.eventType to template.channel] = stored
        return stored
    }

    override fun list() = rows.values.toList()
}
