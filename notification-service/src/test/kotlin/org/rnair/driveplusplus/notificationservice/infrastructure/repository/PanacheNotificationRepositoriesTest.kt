package org.rnair.driveplusplus.notificationservice.infrastructure.repository

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.notificationservice.core.models.DeliveryStatus
import org.rnair.driveplusplus.notificationservice.core.models.NotificationLogEntry
import org.rnair.driveplusplus.notificationservice.core.models.NotificationTemplate
import org.rnair.driveplusplus.notificationservice.core.models.PreferenceCacheEntry
import java.time.Instant
import java.util.UUID

@QuarkusTest
class PanacheNotificationRepositoriesTest {
    @Test
    @TestTransaction
    fun existsForIsPerUser() {
        val repo = PanacheNotificationLogRepository()
        val sourceEventId = UUID.randomUUID()
        val firstUser = UUID.randomUUID()
        val secondUser = UUID.randomUUID()
        repo.save(entry(sourceEventId, firstUser, Instant.parse("2026-10-22T12:00:00Z")))

        assertTrue(repo.existsFor(sourceEventId, firstUser, NotificationChannel.EMAIL))
        assertFalse(repo.existsFor(sourceEventId, secondUser, NotificationChannel.EMAIL))
    }

    @Test
    @TestTransaction
    fun historyFiltersOutOtherUsersAndDates() {
        val repo = PanacheNotificationLogRepository()
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val sourceEventId = UUID.randomUUID()
        repo.save(entry(sourceEventId, userId, Instant.parse("2026-10-02T00:00:00Z")))
        repo.save(entry(UUID.randomUUID(), userId, Instant.parse("2026-10-20T00:00:00Z")))
        repo.save(entry(UUID.randomUUID(), otherUserId, Instant.parse("2026-10-20T00:00:00Z")))

        val rows =
            repo.history(
                userId,
                NotificationEventType.PAYMENT_DUE,
                Instant.parse("2026-10-10T00:00:00Z"),
                Instant.parse("2026-10-31T00:00:00Z"),
            )

        assertEquals(1, rows.size)
        assertEquals(userId, rows.single().userId)
        assertEquals(Instant.parse("2026-10-20T00:00:00Z"), rows.single().sentAt)
    }

    @Test
    @TestTransaction
    fun templateSaveUpdatesTheSameEventTypeAndChannel() {
        val repo = PanacheTemplateRepository()
        val first =
            repo.save(
                NotificationTemplate(
                    UUID.randomUUID(),
                    NotificationEventType.APPOINTMENT_CREATED,
                    NotificationChannel.EMAIL,
                    "First",
                    "Hi {{recipient}}",
                ),
            )
        val second =
            repo.save(
                NotificationTemplate(
                    UUID.randomUUID(),
                    NotificationEventType.APPOINTMENT_CREATED,
                    NotificationChannel.EMAIL,
                    "Second",
                    "Updated {{recipient}}",
                ),
            )

        assertEquals(first.id, second.id)
        assertEquals("Second", second.subjectTemplate)
        assertEquals("Updated {{recipient}}", repo.find(NotificationEventType.APPOINTMENT_CREATED, NotificationChannel.EMAIL)?.bodyTemplate)
    }

    @Test
    @TestTransaction
    fun isEnabledIsTrueWhenTheCacheRowIsMissing() {
        val repo = PanachePreferenceCacheRepository()
        val userId = UUID.randomUUID()

        assertTrue(repo.isEnabled(userId, NotificationEventType.TEST_REMINDER, NotificationChannel.EMAIL))

        repo.upsert(PreferenceCacheEntry(userId, NotificationEventType.TEST_REMINDER, NotificationChannel.EMAIL, false))

        assertFalse(repo.isEnabled(userId, NotificationEventType.TEST_REMINDER, NotificationChannel.EMAIL))
    }

    private fun entry(
        sourceEventId: UUID,
        userId: UUID,
        sentAt: Instant,
    ) = NotificationLogEntry(
        id = UUID.randomUUID(),
        userId = userId,
        sourceEventId = sourceEventId,
        eventType = NotificationEventType.PAYMENT_DUE,
        channel = NotificationChannel.EMAIL,
        status = DeliveryStatus.SENT,
        providerMessageId = "provider-1",
        sentAt = sentAt,
        errorMessage = null,
    )
}
