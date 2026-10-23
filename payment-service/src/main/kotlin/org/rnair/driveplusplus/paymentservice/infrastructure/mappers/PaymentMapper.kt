package org.rnair.driveplusplus.paymentservice.infrastructure.mappers

import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId
import org.rnair.driveplusplus.paymentservice.core.models.Payment
import org.rnair.driveplusplus.paymentservice.core.models.PaymentId
import org.rnair.driveplusplus.paymentservice.infrastructure.entities.PaymentJpaEntity

object PaymentMapper {
    fun toDomain(e: PaymentJpaEntity) = Payment(PaymentId(e.id), InvoiceId(e.invoiceId), e.amount, e.paidAt, e.method)

    fun toEntity(p: Payment) =
        PaymentJpaEntity().apply {
            id = p.id.value
            invoiceId = p.invoiceId.value
            amount = p.amount
            paidAt = p.paidAt
            method = p.method
        }
}
