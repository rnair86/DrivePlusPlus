package org.rnair.driveplusplus.paymentservice.core.models

import java.math.BigDecimal
import java.time.Instant

data class Payment(
    val id: PaymentId,
    val invoiceId: InvoiceId,
    val amount: BigDecimal,
    val paidAt: Instant,
    val method: String,
)
