package org.rnair.driveplusplus.paymentservice.infrastructure.mappers

import org.rnair.driveplusplus.paymentservice.core.models.Invoice
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId
import org.rnair.driveplusplus.paymentservice.infrastructure.entities.InvoiceJpaEntity

object InvoiceMapper {
    fun toDomain(e: InvoiceJpaEntity) =
        Invoice(InvoiceId(e.id), e.studentId, e.enrollmentId, e.amount, e.dueDate, e.status, e.reminderSentAt)

    fun toEntity(i: Invoice) =
        InvoiceJpaEntity().apply {
            id = i.id.value
            studentId = i.studentId
            enrollmentId = i.enrollmentId
            amount = i.amount
            dueDate = i.dueDate
            status = i.status
            reminderSentAt = i.reminderSentAt
        }
}
