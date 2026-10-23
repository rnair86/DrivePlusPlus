package org.rnair.driveplusplus.notificationservice.infrastructure.messaging

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.rnair.driveplusplus.notificationservice.application.usecase.ProcessDomainEventUseCase
import java.time.Instant
import java.util.UUID

data class DomainEventEnvelope(
    val eventId: UUID,
    val eventType: String,
    val occurredAt: Instant,
    val version: String,
    val source: String,
    val correlationId: String? = null,
    val payload: JsonNode = JsonNodeFactory.instance.objectNode(),
)

@ApplicationScoped
class DomainEventListener(
    private val processDomainEventUseCase: ProcessDomainEventUseCase,
) {
    @Incoming("user-events")
    @RunOnVirtualThread
    fun onUserEvent(envelope: DomainEventEnvelope) = dispatch(envelope)

    @Incoming("class-events")
    @RunOnVirtualThread
    fun onClassEvent(envelope: DomainEventEnvelope) = dispatch(envelope)

    @Incoming("appointment-events")
    @RunOnVirtualThread
    fun onAppointmentEvent(envelope: DomainEventEnvelope) = dispatch(envelope)

    @Incoming("test-events")
    @RunOnVirtualThread
    fun onTestEvent(envelope: DomainEventEnvelope) = dispatch(envelope)

    @Incoming("payment-events")
    @RunOnVirtualThread
    fun onPaymentEvent(envelope: DomainEventEnvelope) = dispatch(envelope)

    private fun dispatch(envelope: DomainEventEnvelope) {
        val mapped = DomainEventMapping.map(envelope.eventType, envelope.payload) ?: return
        processDomainEventUseCase.execute(envelope.eventId, mapped.first, mapped.second)
    }
}
