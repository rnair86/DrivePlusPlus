package org.rnair.driveplusplus.paymentservice.infrastructure.messaging

import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.rnair.driveplusplus.paymentservice.application.usecase.EnrollmentCreatedPayload
import org.rnair.driveplusplus.paymentservice.application.usecase.GenerateInvoiceOnEnrollmentUseCase
import java.time.Instant
import java.util.UUID

data class EnrollmentCreatedEnvelope(
    val eventId: UUID,
    val eventType: String,
    val occurredAt: Instant,
    val version: String,
    val source: String,
    val correlationId: String? = null,
    val payload: EnrollmentCreatedPayload,
)

@ApplicationScoped
class EnrollmentCreatedListener(
    private val generateInvoiceOnEnrollmentUseCase: GenerateInvoiceOnEnrollmentUseCase,
) {
    @Incoming("course-enrollment-events")
    @RunOnVirtualThread
    fun onMessage(envelope: EnrollmentCreatedEnvelope) = generateInvoiceOnEnrollmentUseCase.execute(envelope.payload)
}
