package org.rnair.driveplusplus.notificationservice.web.api

import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.notificationservice.application.usecase.GetNotificationHistoryUseCase
import org.rnair.driveplusplus.notificationservice.core.models.NotificationLogEntry
import org.rnair.driveplusplus.notificationservice.web.dtos.NotificationLogResponse
import java.time.Instant
import java.util.UUID

@Path("/api/v1/notifications")
class NotificationHistoryResource(
    private val getNotificationHistoryUseCase: GetNotificationHistoryUseCase,
) {
    @GET
    @RunOnVirtualThread
    fun history(
        @QueryParam("userId") userId: UUID?,
        @QueryParam("eventType") eventType: NotificationEventType?,
        @QueryParam("from") from: Instant?,
        @QueryParam("to") to: Instant?,
    ): Response {
        val entries = getNotificationHistoryUseCase.execute(userId, eventType, from, to).map(::toResponse)
        return Response.ok(entries).build()
    }

    private fun toResponse(entry: NotificationLogEntry) =
        NotificationLogResponse(
            entry.id,
            entry.userId,
            entry.sourceEventId,
            entry.eventType.name,
            entry.channel.name,
            entry.status.name,
            entry.providerMessageId,
            entry.sentAt,
            entry.errorMessage,
        )
}
