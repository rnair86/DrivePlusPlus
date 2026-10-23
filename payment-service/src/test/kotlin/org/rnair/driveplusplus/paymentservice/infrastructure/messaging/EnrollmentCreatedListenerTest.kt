package org.rnair.driveplusplus.paymentservice.infrastructure.messaging

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.paymentservice.application.usecase.EnrollmentCreatedPayload
import org.rnair.driveplusplus.paymentservice.application.usecase.GenerateInvoiceOnEnrollmentUseCase
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class EnrollmentCreatedListenerTest {
    @Test
    fun forwardsEnrollmentPayloadToInvoiceGeneration() {
        val generate = mockk<GenerateInvoiceOnEnrollmentUseCase>()
        val payload =
            EnrollmentCreatedPayload(
                enrollmentId = UUID.randomUUID(),
                studentId = UUID.randomUUID(),
                batchId = UUID.randomUUID(),
                courseId = UUID.randomUUID(),
                coursePrice = BigDecimal("250.00"),
            )
        val envelope =
            EnrollmentCreatedEnvelope(
                eventId = UUID.randomUUID(),
                eventType = "course.enrollment.created",
                occurredAt = Instant.parse("2026-10-22T12:00:00Z"),
                version = "1.0",
                source = "course-service",
                payload = payload,
            )
        every { generate.execute(payload) } just runs

        EnrollmentCreatedListener(generate).onMessage(envelope)

        verify(exactly = 1) { generate.execute(payload) }
    }
}
