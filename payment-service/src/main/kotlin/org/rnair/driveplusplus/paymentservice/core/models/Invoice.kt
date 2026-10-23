package org.rnair.driveplusplus.paymentservice.core.models

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Invoice(
    val id: InvoiceId,
    val studentId: UUID,
    val enrollmentId: UUID?,
    val amount: BigDecimal,
    val dueDate: LocalDate,
    val status: InvoiceStatus = InvoiceStatus.PENDING,
    val reminderSentAt: Instant? = null,
)
