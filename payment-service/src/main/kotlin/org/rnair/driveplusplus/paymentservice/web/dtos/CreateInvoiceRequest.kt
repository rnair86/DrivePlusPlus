package org.rnair.driveplusplus.paymentservice.web.dtos

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CreateInvoiceRequest(
    val studentId: UUID,
    val enrollmentId: UUID?,
    val amount: BigDecimal,
    val dueDate: LocalDate,
)
