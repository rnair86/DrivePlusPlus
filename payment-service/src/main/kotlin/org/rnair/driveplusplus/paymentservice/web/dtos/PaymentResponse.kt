package org.rnair.driveplusplus.paymentservice.web.dtos

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaymentResponse(
    val id: UUID,
    val invoiceId: UUID,
    val amount: BigDecimal,
    val paidAt: Instant,
    val method: String,
)
