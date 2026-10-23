package org.rnair.driveplusplus.notificationpreferenceservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.notificationpreferenceservice.core.models.NotificationPreference
import org.rnair.driveplusplus.notificationpreferenceservice.core.repository.NotificationPreferenceRepository
import java.time.LocalTime
import java.util.UUID

class PreferenceUseCasesTest {
    @Test
    fun upsertPublishesOneEventPerRowAndLeavesOtherRowsInPlace() {
        val userId = UUID.fromString("00000000-0000-0000-0000-00000000c001")
        val repo = InMemoryPreferenceRepository()
        val kept =
            preference(
                userId,
                NotificationEventType.PAYMENT_RECEIVED,
                NotificationChannel.SMS,
            )
        repo.upsert(kept)
        val publisher = mockk<DomainEventPublisher>()
        val published = mutableListOf<EventEnvelope<*>>()
        every { publisher.publish(capture(published)) } just runs
        val requested =
            listOf(
                preference(userId, NotificationEventType.APPOINTMENT_CREATED, NotificationChannel.EMAIL),
                preference(UUID.randomUUID(), NotificationEventType.TEST_REMINDER, NotificationChannel.PUSH, enabled = false),
            )

        val saved = UpsertPreferencesUseCase(repo, publisher).execute(userId, requested)

        assertEquals(userId, saved[0].userId)
        assertEquals(userId, saved[1].userId)
        assertEquals(2, published.size)
        assertTrue(published.all { it.eventType == "preference.updated" && it.source == "notification-preference-service" })
        val payloads = published.map { it.payload as PreferenceUpdatedPayload }
        assertEquals("APPOINTMENT_CREATED", payloads[0].eventType)
        assertEquals("EMAIL", payloads[0].channel)
        assertEquals(true, payloads[0].enabled)
        assertEquals("TEST_REMINDER", payloads[1].eventType)
        assertEquals(false, payloads[1].enabled)
        assertEquals(3, repo.findAllForUser(userId).size)
        assertEquals(kept, repo.findOne(userId, NotificationEventType.PAYMENT_RECEIVED, NotificationChannel.SMS))
    }

    @Test
    fun secondUpsertUpdatesEnabledAndQuietHours() {
        val userId = UUID.randomUUID()
        val repo = InMemoryPreferenceRepository()
        val publisher = mockk<DomainEventPublisher>()
        every { publisher.publish(any()) } just runs
        val useCase = UpsertPreferencesUseCase(repo, publisher)
        useCase.execute(userId, listOf(preference(userId, NotificationEventType.PAYMENT_DUE, NotificationChannel.EMAIL)))

        val updated =
            useCase.execute(
                userId,
                listOf(
                    preference(
                        userId,
                        NotificationEventType.PAYMENT_DUE,
                        NotificationChannel.EMAIL,
                        enabled = false,
                        quietHoursStart = LocalTime.of(22, 0),
                        quietHoursEnd = LocalTime.of(7, 0),
                    ),
                ),
            )

        assertEquals(1, repo.findAllForUser(userId).size)
        assertEquals(false, updated.single().enabled)
        assertEquals(LocalTime.of(22, 0), updated.single().quietHoursStart)
        assertEquals(LocalTime.of(7, 0), updated.single().quietHoursEnd)
    }

    @Test
    fun toggleKeepsQuietHoursOnAnExistingRow() {
        val userId = UUID.randomUUID()
        val repo = InMemoryPreferenceRepository()
        repo.upsert(
            preference(
                userId,
                NotificationEventType.APPOINTMENT_REMINDER,
                NotificationChannel.EMAIL,
                quietHoursStart = LocalTime.of(21, 0),
                quietHoursEnd = LocalTime.of(6, 0),
            ),
        )
        val publisher = mockk<DomainEventPublisher>()
        val published = mutableListOf<EventEnvelope<*>>()
        every { publisher.publish(capture(published)) } just runs

        val saved =
            TogglePreferenceUseCase(repo, publisher).execute(
                userId,
                NotificationEventType.APPOINTMENT_REMINDER,
                NotificationChannel.EMAIL,
                false,
            )

        assertEquals(false, saved.enabled)
        assertEquals(LocalTime.of(21, 0), saved.quietHoursStart)
        assertEquals(LocalTime.of(6, 0), saved.quietHoursEnd)
        val payload = published.single().payload as PreferenceUpdatedPayload
        assertEquals(userId, payload.userId)
        assertEquals(false, payload.enabled)
        assertEquals("APPOINTMENT_REMINDER", payload.eventType)
    }

    @Test
    fun toggleInsertsAMissingRowWithNullQuietHours() {
        val userId = UUID.randomUUID()
        val repo = InMemoryPreferenceRepository()
        val publisher = mockk<DomainEventPublisher>()
        every { publisher.publish(any()) } just runs

        val saved =
            TogglePreferenceUseCase(repo, publisher).execute(
                userId,
                NotificationEventType.TEST_SCHEDULED,
                NotificationChannel.SMS,
                true,
            )

        assertEquals(true, saved.enabled)
        assertNull(saved.quietHoursStart)
        assertNull(saved.quietHoursEnd)
        assertEquals(saved, repo.findOne(userId, NotificationEventType.TEST_SCHEDULED, NotificationChannel.SMS))
    }

    @Test
    fun studentSeedWritesSevenEmailRowsAndASecondSeedDoesNotDuplicateThem() {
        val userId = UUID.randomUUID()
        val repo = InMemoryPreferenceRepository()

        SeedDefaultPreferencesUseCase(repo).execute(userId, "STUDENT")
        SeedDefaultPreferencesUseCase(repo).execute(userId, "STUDENT")

        val rows = repo.findAllForUser(userId)
        assertEquals(7, rows.size)
        assertTrue(rows.all { it.channel == NotificationChannel.EMAIL && it.enabled && it.quietHoursStart == null })
        assertEquals(
            setOf(
                NotificationEventType.APPOINTMENT_CREATED,
                NotificationEventType.APPOINTMENT_REMINDER,
                NotificationEventType.TEST_SCHEDULED,
                NotificationEventType.TEST_REMINDER,
                NotificationEventType.TEST_RESULT_PUBLISHED,
                NotificationEventType.PAYMENT_DUE,
                NotificationEventType.PAYMENT_OVERDUE,
            ),
            rows.map { it.eventType }.toSet(),
        )
    }

    @Test
    fun instructorSeedWritesTwoEmailRows() {
        val userId = UUID.randomUUID()
        val repo = InMemoryPreferenceRepository()

        SeedDefaultPreferencesUseCase(repo).execute(userId, "INSTRUCTOR")

        val rows = repo.findAllForUser(userId)
        assertEquals(
            setOf(NotificationEventType.APPOINTMENT_CREATED, NotificationEventType.CLASS_SESSION_SCHEDULED),
            rows.map { it.eventType }.toSet(),
        )
        assertTrue(rows.all { it.channel == NotificationChannel.EMAIL && it.enabled })
    }

    private fun preference(
        userId: UUID,
        eventType: NotificationEventType,
        channel: NotificationChannel,
        enabled: Boolean = true,
        quietHoursStart: LocalTime? = null,
        quietHoursEnd: LocalTime? = null,
    ) = NotificationPreference(userId, eventType, channel, enabled, quietHoursStart, quietHoursEnd)
}

private class InMemoryPreferenceRepository : NotificationPreferenceRepository {
    private val rows = linkedMapOf<Triple<UUID, NotificationEventType, NotificationChannel>, NotificationPreference>()

    override fun upsert(preference: NotificationPreference): NotificationPreference {
        rows[Triple(preference.userId, preference.eventType, preference.channel)] = preference
        return preference
    }

    override fun findAllForUser(userId: UUID) = rows.values.filter { it.userId == userId }

    override fun findOne(
        userId: UUID,
        eventType: NotificationEventType,
        channel: NotificationChannel,
    ) = rows[Triple(userId, eventType, channel)]
}
