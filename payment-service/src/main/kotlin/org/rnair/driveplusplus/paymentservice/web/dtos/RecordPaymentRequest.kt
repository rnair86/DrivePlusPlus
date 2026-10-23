package org.rnair.driveplusplus.paymentservice.web.dtos

import java.math.BigDecimal

data class RecordPaymentRequest(
    val amount: BigDecimal,
    val method: String,
)
