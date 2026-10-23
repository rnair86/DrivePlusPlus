package org.rnair.driveplusplus.notificationpreferenceservice.web.api

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.notificationpreferenceservice.application.usecase.GetPreferenceMatrixUseCase
import org.rnair.driveplusplus.notificationpreferenceservice.application.usecase.TogglePreferenceUseCase
import org.rnair.driveplusplus.notificationpreferenceservice.application.usecase.UpsertPreferencesUseCase
import org.rnair.driveplusplus.notificationpreferenceservice.core.models.NotificationPreference
import org.rnair.driveplusplus.notificationpreferenceservice.web.dtos.TogglePreferenceRequest
import org.rnair.driveplusplus.notificationpreferenceservice.web.dtos.UpsertPreferenceRequest
import org.rnair.driveplusplus.notificationpreferenceservice.web.dtos.UpsertPreferencesRequest
import java.time.LocalTime
import java.util.UUID

class PreferenceResourceTest {
    @Test
    fun listsPreferencesForThePathUser() {
        val list = mockk<GetPreferenceMatrixUseCase>()
        val userId = UUID.fromString("00000000-0000-0000-0000-00000000c201")
        every { list.execute(userId) } returns
            listOf(
                NotificationPreference(
                    userId,
                    NotificationEventType.APPOINTMENT_CREATED,
                    NotificationChannel.EMAIL,
                    quietHoursStart = LocalTime.of(22, 0),
                ),
            )

        val result = resource(get = list).list(userId)

        assertEquals(userId, result.single().userId)
        assertEquals("APPOINTMENT_CREATED", result.single().eventType)
        assertEquals("EMAIL", result.single().channel)
        assertEquals(LocalTime.of(22, 0), result.single().quietHoursStart)
    }

    @Test
    fun upsertUsesThePathUserIdAndReturnsOk() {
        val upsert = mockk<UpsertPreferencesUseCase>()
        val pathUserId = UUID.fromString("00000000-0000-0000-0000-00000000c202")
        val start = LocalTime.of(21, 30)
        val end = LocalTime.of(6, 15)
        every { upsert.execute(any(), any()) } answers { secondArg() }
        val request =
            UpsertPreferencesRequest(
                listOf(
                    UpsertPreferenceRequest(
                        eventType = NotificationEventType.PAYMENT_DUE,
                        channel = NotificationChannel.SMS,
                        enabled = false,
                        quietHoursStart = start,
                        quietHoursEnd = end,
                    ),
                ),
            )

        val response = resource(upsert = upsert).upsert(pathUserId, request)

        assertEquals(200, response.status)
        verify(exactly = 1) {
            upsert.execute(
                pathUserId,
                listOf(
                    NotificationPreference(
                        userId = pathUserId,
                        eventType = NotificationEventType.PAYMENT_DUE,
                        channel = NotificationChannel.SMS,
                        enabled = false,
                        quietHoursStart = start,
                        quietHoursEnd = end,
                    ),
                ),
            )
        }
    }

    @Test
    fun togglesEnabledAndReturnsOk() {
        val toggle = mockk<TogglePreferenceUseCase>()
        val userId = UUID.fromString("00000000-0000-0000-0000-00000000c203")
        every { toggle.execute(any(), any(), any(), any()) } returns
            NotificationPreference(userId, NotificationEventType.TEST_REMINDER, NotificationChannel.EMAIL, enabled = false)

        val response =
            resource(toggle = toggle).toggle(
                userId,
                NotificationEventType.TEST_REMINDER,
                NotificationChannel.EMAIL,
                TogglePreferenceRequest(enabled = false),
            )

        assertEquals(200, response.status)
        verify(exactly = 1) {
            toggle.execute(userId, NotificationEventType.TEST_REMINDER, NotificationChannel.EMAIL, false)
        }
    }

    private fun resource(
        upsert: UpsertPreferencesUseCase = mockk(),
        get: GetPreferenceMatrixUseCase = mockk(),
        toggle: TogglePreferenceUseCase = mockk(),
    ) = PreferenceResource(upsert, get, toggle)
}
