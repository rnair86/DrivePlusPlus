package org.rnair.driveplusplus.notificationpreferenceservice.web.api

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.notificationpreferenceservice.application.usecase.GetPreferenceMatrixUseCase
import org.rnair.driveplusplus.notificationpreferenceservice.application.usecase.TogglePreferenceUseCase
import org.rnair.driveplusplus.notificationpreferenceservice.application.usecase.UpsertPreferencesUseCase
import org.rnair.driveplusplus.notificationpreferenceservice.core.models.NotificationPreference
import org.rnair.driveplusplus.notificationpreferenceservice.web.dtos.PreferenceResponse
import org.rnair.driveplusplus.notificationpreferenceservice.web.dtos.TogglePreferenceRequest
import org.rnair.driveplusplus.notificationpreferenceservice.web.dtos.UpsertPreferenceRequest
import org.rnair.driveplusplus.notificationpreferenceservice.web.dtos.UpsertPreferencesRequest
import java.util.UUID

@Path("/api/v1/users/{userId}/preferences")
class PreferenceResource(
    private val upsertPreferencesUseCase: UpsertPreferencesUseCase,
    private val getPreferenceMatrixUseCase: GetPreferenceMatrixUseCase,
    private val togglePreferenceUseCase: TogglePreferenceUseCase,
) {
    @GET
    @RunOnVirtualThread
    fun list(
        @PathParam("userId") userId: UUID,
    ): List<PreferenceResponse> = getPreferenceMatrixUseCase.execute(userId).map(::toResponse)

    @PUT
    @RunOnVirtualThread
    fun upsert(
        @PathParam("userId") userId: UUID,
        request: UpsertPreferencesRequest,
    ): Response {
        val saved = upsertPreferencesUseCase.execute(userId, request.preferences.map { toDomain(userId, it) })
        return Response.ok(saved.map(::toResponse)).build()
    }

    @PUT
    @Path("/{eventType}/{channel}")
    @RunOnVirtualThread
    fun toggle(
        @PathParam("userId") userId: UUID,
        @PathParam("eventType") eventType: NotificationEventType,
        @PathParam("channel") channel: NotificationChannel,
        request: TogglePreferenceRequest,
    ): Response {
        val saved = togglePreferenceUseCase.execute(userId, eventType, channel, request.enabled)
        return Response.ok(toResponse(saved)).build()
    }

    private fun toDomain(
        userId: UUID,
        request: UpsertPreferenceRequest,
    ) = NotificationPreference(
        userId = userId,
        eventType = request.eventType,
        channel = request.channel,
        enabled = request.enabled,
        quietHoursStart = request.quietHoursStart,
        quietHoursEnd = request.quietHoursEnd,
    )

    private fun toResponse(preference: NotificationPreference) =
        PreferenceResponse(
            preference.userId,
            preference.eventType.name,
            preference.channel.name,
            preference.enabled,
            preference.quietHoursStart,
            preference.quietHoursEnd,
        )
}
