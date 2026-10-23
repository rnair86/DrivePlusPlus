package org.rnair.driveplusplus.notificationservice.web.api

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.notificationservice.application.usecase.GetNotificationHistoryUseCase
import org.rnair.driveplusplus.notificationservice.application.usecase.ListTemplatesUseCase
import org.rnair.driveplusplus.notificationservice.application.usecase.UpdateTemplateUseCase
import org.rnair.driveplusplus.notificationservice.core.models.DeliveryStatus
import org.rnair.driveplusplus.notificationservice.core.models.NotificationLogEntry
import org.rnair.driveplusplus.notificationservice.core.models.NotificationTemplate
import org.rnair.driveplusplus.notificationservice.web.dtos.TemplateRequest
import java.time.Instant
import java.util.UUID

class NotificationResourceTest {
    @Test
    fun historyForwardsFiltersAndReturnsOk() {
        val history = mockk<GetNotificationHistoryUseCase>()
        val userId = UUID.fromString("00000000-0000-0000-0000-00000000d201")
        val from = Instant.parse("2026-10-01T00:00:00Z")
        val to = Instant.parse("2026-10-31T00:00:00Z")
        every { history.execute(any(), any(), any(), any()) } returns
            listOf(
                NotificationLogEntry(
                    UUID.randomUUID(),
                    userId,
                    UUID.randomUUID(),
                    NotificationEventType.PAYMENT_DUE,
                    NotificationChannel.EMAIL,
                    DeliveryStatus.SENT,
                    "provider-1",
                    from,
                    null,
                ),
            )

        val response =
            NotificationHistoryResource(history).history(userId, NotificationEventType.PAYMENT_DUE, from, to)

        assertEquals(200, response.status)
        verify(exactly = 1) { history.execute(userId, NotificationEventType.PAYMENT_DUE, from, to) }
    }

    @Test
    fun listsTemplatesAndReturnsOk() {
        val list = mockk<ListTemplatesUseCase>()
        every { list.execute() } returns emptyList()

        val response = TemplateResource(list, mockk()).list()

        assertEquals(200, response.status)
        verify(exactly = 1) { list.execute() }
    }

    @Test
    fun savesTemplateFieldsAndReturnsOk() {
        val update = mockk<UpdateTemplateUseCase>()
        every { update.execute(any(), any(), any(), any()) } returns
            NotificationTemplate(
                UUID.randomUUID(),
                NotificationEventType.TEST_REMINDER,
                NotificationChannel.EMAIL,
                "Upcoming test",
                "Hi {{recipient}}",
            )

        val response =
            TemplateResource(mockk(), update).save(
                TemplateRequest(
                    NotificationEventType.TEST_REMINDER,
                    NotificationChannel.EMAIL,
                    "Upcoming test",
                    "Hi {{recipient}}",
                ),
            )

        assertEquals(200, response.status)
        verify(exactly = 1) {
            update.execute(NotificationEventType.TEST_REMINDER, NotificationChannel.EMAIL, "Upcoming test", "Hi {{recipient}}")
        }
    }
}
