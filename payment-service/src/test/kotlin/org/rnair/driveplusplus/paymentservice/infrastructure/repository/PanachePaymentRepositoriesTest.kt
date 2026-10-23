package org.rnair.driveplusplus.paymentservice.infrastructure.repository

import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.paymentservice.core.models.Invoice
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceStatus
import org.rnair.driveplusplus.paymentservice.core.models.Payment
import org.rnair.driveplusplus.paymentservice.core.models.PaymentId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@QuarkusTest
class PanachePaymentRepositoriesTest {
    @Test
    @TestTransaction
    fun sumsPaymentsForAnInvoice() {
        val invoices = PanacheInvoiceRepository()
        val payments = PanachePaymentRepository()
        val invoice = invoices.save(invoice(LocalDate.of(2026, 11, 5)))

        assertEquals(0, payments.sumPaidForInvoice(invoice.id).compareTo(BigDecimal.ZERO))

        payments.save(payment(invoice.id, BigDecimal("40.00")))
        payments.save(payment(invoice.id, BigDecimal("10.00")))

        assertEquals(0, payments.sumPaidForInvoice(invoice.id).compareTo(BigDecimal("50.00")))
    }

    @Test
    @TestTransaction
    fun marksOnlyPendingInvoicesDueBeforeTheGivenDate() {
        val invoices = PanacheInvoiceRepository()
        val asOf = LocalDate.of(2026, 10, 22)
        val overdue = invoices.save(invoice(LocalDate.of(2026, 10, 21)))
        val dueToday = invoices.save(invoice(asOf))
        val alreadyPaid = invoices.save(invoice(LocalDate.of(2026, 10, 21), InvoiceStatus.PAID))
        val future = invoices.save(invoice(LocalDate.of(2026, 10, 23)))

        val marked = invoices.markOverdue(asOf)
        invoices.getEntityManager().clear()

        assertEquals(listOf(overdue.id), marked.map { it.id })
        assertEquals(InvoiceStatus.OVERDUE, invoices.findByInvoiceId(overdue.id)?.status)
        assertEquals(InvoiceStatus.PENDING, invoices.findByInvoiceId(dueToday.id)?.status)
        assertEquals(InvoiceStatus.PAID, invoices.findByInvoiceId(alreadyPaid.id)?.status)
        assertEquals(InvoiceStatus.PENDING, invoices.findByInvoiceId(future.id)?.status)
    }

    @Test
    @TestTransaction
    fun dueRemindersSkipSentNonPendingAndFarFutureInvoices() {
        val invoices = PanacheInvoiceRepository()
        val due = invoices.save(invoice(LocalDate.now().plusDays(2)))
        invoices.save(invoice(LocalDate.now().plusDays(1)).copy(reminderSentAt = Instant.parse("2026-10-01T00:00:00Z")))
        invoices.save(invoice(LocalDate.now(), InvoiceStatus.OVERDUE))
        invoices.save(invoice(LocalDate.now().plusDays(10)))

        val dueIds = invoices.findDueReminders(3).map { it.id }

        assertEquals(listOf(due.id), dueIds)
    }

    private fun invoice(
        dueDate: LocalDate,
        status: InvoiceStatus = InvoiceStatus.PENDING,
    ) = Invoice(
        id = InvoiceId(UUID.randomUUID()),
        studentId = UUID.randomUUID(),
        enrollmentId = null,
        amount = BigDecimal("100.00"),
        dueDate = dueDate,
        status = status,
    )

    private fun payment(
        invoiceId: InvoiceId,
        amount: BigDecimal,
    ) = Payment(
        id = PaymentId(UUID.randomUUID()),
        invoiceId = invoiceId,
        amount = amount,
        paidAt = Instant.parse("2026-10-22T12:00:00Z"),
        method = "CARD",
    )
}
