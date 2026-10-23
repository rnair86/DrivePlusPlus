package org.rnair.driveplusplus.paymentservice.core.models

import java.util.UUID

@JvmInline
value class InvoiceId(
    val value: UUID,
)

@JvmInline
value class PaymentId(
    val value: UUID,
)
