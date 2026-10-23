package org.rnair.driveplusplus.notificationpreferenceservice.infrastructure.repository

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.notificationpreferenceservice.core.models.NotificationPreference
import java.time.LocalTime
import java.util.UUID

@QuarkusTest
class PanacheNotificationPreferenceRepositoryTest {
    @Test
    @TestTransaction
    fun upsertInsertsThenUpdatesTheSameKeyAndDoesNotReturnAnotherUsersRows() {
        val repo = PanacheNotificationPreferenceRepository()
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        repo.upsert(preference(userId, NotificationEventType.APPOINTMENT_CREATED, NotificationChannel.EMAIL))
        repo.upsert(preference(otherUserId, NotificationEventType.APPOINTMENT_CREATED, NotificationChannel.EMAIL))

        val updated =
            repo.upsert(
                preference(
                    userId,
                    NotificationEventType.APPOINTMENT_CREATED,
                    NotificationChannel.EMAIL,
                    enabled = false,
                    quietHoursStart = LocalTime.of(22, 0),
                    quietHoursEnd = LocalTime.of(7, 0),
                ),
            )

        assertEquals(false, updated.enabled)
        assertEquals(LocalTime.of(22, 0), updated.quietHoursStart)
        assertEquals(LocalTime.of(7, 0), updated.quietHoursEnd)
        val rows = repo.findAllForUser(userId)
        assertEquals(1, rows.size)
        assertEquals(updated, rows.single())
        assertNull(repo.findOne(userId, NotificationEventType.TEST_SCHEDULED, NotificationChannel.EMAIL))
        assertEquals(1, repo.findAllForUser(otherUserId).size)
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
