package org.rnair.driveplusplus.paymentservice.core.repository

import org.rnair.driveplusplus.paymentservice.core.models.Invoice
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceStatus
import java.time.LocalDate
import java.util.UUID

interface InvoiceRepository {
    fun save(invoice: Invoice): Invoice

    fun findByInvoiceId(id: InvoiceId): Invoice?

    fun findByIdForUpdate(id: InvoiceId): Invoice?

    fun listByStudent(studentId: UUID): List<Invoice>

    fun findDueReminders(withinDays: Long): List<Invoice>

    fun markReminderSent(id: InvoiceId)

    fun markOverdue(asOf: LocalDate): List<Invoice>

    fun updateStatus(
        id: InvoiceId,
        status: InvoiceStatus,
    )
}
