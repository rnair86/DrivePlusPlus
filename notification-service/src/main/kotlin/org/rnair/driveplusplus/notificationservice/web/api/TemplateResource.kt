package org.rnair.driveplusplus.notificationservice.web.api

import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.notificationservice.application.usecase.ListTemplatesUseCase
import org.rnair.driveplusplus.notificationservice.application.usecase.UpdateTemplateUseCase
import org.rnair.driveplusplus.notificationservice.core.models.NotificationTemplate
import org.rnair.driveplusplus.notificationservice.web.dtos.TemplateRequest
import org.rnair.driveplusplus.notificationservice.web.dtos.TemplateResponse

@Path("/api/v1/templates")
class TemplateResource(
    private val listTemplatesUseCase: ListTemplatesUseCase,
    private val updateTemplateUseCase: UpdateTemplateUseCase,
) {
    @GET
    @RunOnVirtualThread
    fun list(): Response = Response.ok(listTemplatesUseCase.execute().map(::toResponse)).build()

    @PUT
    @RunOnVirtualThread
    fun save(request: TemplateRequest): Response {
        val saved =
            updateTemplateUseCase.execute(
                request.eventType,
                request.channel,
                request.subjectTemplate,
                request.bodyTemplate,
            )
        return Response.ok(toResponse(saved)).build()
    }

    private fun toResponse(template: NotificationTemplate) =
        TemplateResponse(
            template.id,
            template.eventType.name,
            template.channel.name,
            template.subjectTemplate,
            template.bodyTemplate,
        )
}
