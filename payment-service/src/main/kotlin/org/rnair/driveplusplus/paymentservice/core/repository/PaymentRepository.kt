package org.rnair.driveplusplus.paymentservice.core.repository

import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId
import org.rnair.driveplusplus.paymentservice.core.models.Payment
import java.math.BigDecimal

interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun sumPaidForInvoice(invoiceId: InvoiceId): BigDecimal
}
