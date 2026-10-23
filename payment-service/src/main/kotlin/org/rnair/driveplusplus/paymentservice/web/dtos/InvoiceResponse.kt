package org.rnair.driveplusplus.paymentservice.web.dtos

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class InvoiceResponse(
    val id: UUID,
    val studentId: UUID,
    val enrollmentId: UUID?,
    val amount: BigDecimal,
    val dueDate: LocalDate,
    val status: String,
    val reminderSentAt: Instant?,
)
