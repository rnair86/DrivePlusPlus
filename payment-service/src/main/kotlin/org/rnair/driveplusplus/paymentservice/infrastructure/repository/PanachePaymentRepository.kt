package org.rnair.driveplusplus.paymentservice.infrastructure.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId
import org.rnair.driveplusplus.paymentservice.core.models.Payment
import org.rnair.driveplusplus.paymentservice.core.repository.PaymentRepository
import org.rnair.driveplusplus.paymentservice.infrastructure.entities.PaymentJpaEntity
import org.rnair.driveplusplus.paymentservice.infrastructure.mappers.PaymentMapper
import java.math.BigDecimal
import java.util.UUID

@ApplicationScoped
class PanachePaymentRepository :
    PaymentRepository,
    PanacheRepositoryBase<PaymentJpaEntity, UUID> {
    override fun save(payment: Payment): Payment {
        val entity = PaymentMapper.toEntity(payment)
        persist(entity)
        return PaymentMapper.toDomain(entity)
    }

    override fun sumPaidForInvoice(invoiceId: InvoiceId): BigDecimal = find("invoiceId", invoiceId.value).list().sumOf { it.amount }
}
