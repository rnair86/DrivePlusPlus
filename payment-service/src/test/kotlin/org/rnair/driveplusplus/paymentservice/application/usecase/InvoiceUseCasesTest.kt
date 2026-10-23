package org.rnair.driveplusplus.paymentservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.paymentservice.core.exceptions.InvoiceAlreadyPaidException
import org.rnair.driveplusplus.paymentservice.core.exceptions.InvoiceCancelledException
import org.rnair.driveplusplus.paymentservice.core.exceptions.OverpaymentException
import org.rnair.driveplusplus.paymentservice.core.models.Invoice
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceStatus
import org.rnair.driveplusplus.paymentservice.core.repository.InvoiceRepository
import org.rnair.driveplusplus.paymentservice.core.repository.PaymentRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class InvoiceUseCasesTest {
    @Test
    fun createsInvoiceAndPublishesCreatedEvent() {
        val studentId = UUID.fromString("00000000-0000-0000-0000-00000000b001")
        val enrollmentId = UUID.fromString("00000000-0000-0000-0000-00000000b002")
        val dueDate = LocalDate.of(2026, 11, 5)
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<InvoiceRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.save(any()) } answers { firstArg() }
        every { publisher.publish(capture(envelope)) } just runs

        val saved = CreateInvoiceUseCase(repo, publisher).execute(studentId, enrollmentId, BigDecimal("150.00"), dueDate)

        assertEquals(studentId, saved.studentId)
        assertEquals(enrollmentId, saved.enrollmentId)
        assertEquals(InvoiceStatus.PENDING, saved.status)
        assertEquals("payment.invoice.created", envelope.captured.eventType)
        val payload = envelope.captured.payload as InvoiceCreatedPayload
        assertEquals(saved.id.value, payload.invoiceId)
        assertEquals(0, BigDecimal("150.00").compareTo(payload.amount))
        assertEquals(dueDate, payload.dueDate)
    }

    @Test
    fun generatesInvoiceDueFourteenDaysAfterEnrollment() {
        val create = mockk<CreateInvoiceUseCase>()
        val payload =
            EnrollmentCreatedPayload(
                enrollmentId = UUID.randomUUID(),
                studentId = UUID.randomUUID(),
                batchId = UUID.randomUUID(),
                courseId = UUID.randomUUID(),
                coursePrice = BigDecimal("499.50"),
            )
        every { create.execute(any(), any(), any(), any()) } returns invoice()

        GenerateInvoiceOnEnrollmentUseCase(create).execute(payload)

        verify(exactly = 1) {
            create.execute(payload.studentId, payload.enrollmentId, payload.coursePrice, LocalDate.now().plusDays(14))
        }
    }

    @Test
    fun recordsPartialPaymentWithoutMarkingTheInvoicePaid() {
        val existing = invoice(amount = BigDecimal("100.00"))
        val envelope = slot<EventEnvelope<*>>()
        val invoices = mockk<InvoiceRepository>()
        val payments = mockk<PaymentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { invoices.findByIdForUpdate(existing.id) } returns existing
        every { payments.sumPaidForInvoice(existing.id) } returns BigDecimal("40.00")
        every { payments.save(any()) } answers { firstArg() }
        every { publisher.publish(capture(envelope)) } just runs

        val payment = RecordPaymentUseCase(invoices, payments, publisher).execute(existing.id, BigDecimal("10.0"), "CARD")

        assertEquals("CARD", payment.method)
        assertEquals(0, BigDecimal("10.0").compareTo(payment.amount))
        assertEquals("payment.received", envelope.captured.eventType)
        val payload = envelope.captured.payload as PaymentReceivedPayload
        assertEquals(existing.studentId, payload.studentId)
        verify(exactly = 0) { invoices.updateStatus(any(), any()) }
    }

    @Test
    fun marksInvoicePaidWhenThePaymentCompletesTheBalance() {
        val existing = invoice(amount = BigDecimal("100.00"), status = InvoiceStatus.OVERDUE)
        val invoices = mockk<InvoiceRepository>()
        val payments = mockk<PaymentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { invoices.findByIdForUpdate(existing.id) } returns existing
        every { payments.sumPaidForInvoice(existing.id) } returns BigDecimal("40.00")
        every { payments.save(any()) } answers { firstArg() }
        every { invoices.updateStatus(existing.id, InvoiceStatus.PAID) } just runs
        every { publisher.publish(any()) } just runs

        RecordPaymentUseCase(invoices, payments, publisher).execute(existing.id, BigDecimal("60.00"), "CASH")

        verify(exactly = 1) { invoices.updateStatus(existing.id, InvoiceStatus.PAID) }
        verify(exactly = 1) { publisher.publish(any()) }
    }

    @Test
    fun throwsWhenPaymentExceedsTheBalanceAndPublishesNothing() {
        val existing = invoice(amount = BigDecimal("100.00"))
        val invoices = mockk<InvoiceRepository>()
        val payments = mockk<PaymentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { invoices.findByIdForUpdate(existing.id) } returns existing
        every { payments.sumPaidForInvoice(existing.id) } returns BigDecimal("90.00")

        assertThrows<OverpaymentException> {
            RecordPaymentUseCase(invoices, payments, publisher).execute(existing.id, BigDecimal("20.00"), "CASH")
        }
        verify(exactly = 0) { payments.save(any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun throwsWhenPayingAPaidInvoice() {
        val existing = invoice(status = InvoiceStatus.PAID)
        val invoices = mockk<InvoiceRepository>()
        val payments = mockk<PaymentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { invoices.findByIdForUpdate(existing.id) } returns existing

        assertThrows<InvoiceAlreadyPaidException> {
            RecordPaymentUseCase(invoices, payments, publisher).execute(existing.id, BigDecimal.TEN, "CASH")
        }
        verify(exactly = 0) { payments.save(any()) }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun throwsWhenPayingACancelledInvoice() {
        val existing = invoice(status = InvoiceStatus.CANCELLED)
        val invoices = mockk<InvoiceRepository>()
        val payments = mockk<PaymentRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { invoices.findByIdForUpdate(existing.id) } returns existing

        assertThrows<InvoiceCancelledException> {
            RecordPaymentUseCase(invoices, payments, publisher).execute(existing.id, BigDecimal.TEN, "CASH")
        }
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun cancelsPendingInvoiceWithoutPublishing() {
        val existing = invoice()
        val repo = mockk<InvoiceRepository>()
        every { repo.findByInvoiceId(existing.id) } returns existing
        every { repo.updateStatus(existing.id, InvoiceStatus.CANCELLED) } just runs

        CancelInvoiceUseCase(repo).execute(existing.id)

        verify(exactly = 1) { repo.updateStatus(existing.id, InvoiceStatus.CANCELLED) }
    }

    @Test
    fun throwsWhenCancellingAPaidInvoice() {
        val existing = invoice(status = InvoiceStatus.PAID)
        val repo = mockk<InvoiceRepository>()
        every { repo.findByInvoiceId(existing.id) } returns existing

        assertThrows<InvoiceAlreadyPaidException> { CancelInvoiceUseCase(repo).execute(existing.id) }
        verify(exactly = 0) { repo.updateStatus(any(), any()) }
    }

    @Test
    fun throwsWhenCancellingAnAlreadyCancelledInvoice() {
        val existing = invoice(status = InvoiceStatus.CANCELLED)
        val repo = mockk<InvoiceRepository>()
        every { repo.findByInvoiceId(existing.id) } returns existing

        assertThrows<InvoiceCancelledException> { CancelInvoiceUseCase(repo).execute(existing.id) }
        verify(exactly = 0) { repo.updateStatus(any(), any()) }
    }

    @Test
    fun publishesDueReminderAndMarksItSent() {
        val existing = invoice()
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<InvoiceRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.findDueReminders(3) } returns listOf(existing)
        every { publisher.publish(capture(envelope)) } just runs
        every { repo.markReminderSent(existing.id) } just runs

        FindDueReminderInvoicesUseCase(repo, publisher).execute()

        assertEquals("payment.reminder.due", envelope.captured.eventType)
        val payload = envelope.captured.payload as PaymentReminderDuePayload
        assertEquals(existing.id.value, payload.invoiceId)
        assertEquals(existing.dueDate, payload.dueDate)
        verify(exactly = 1) { repo.markReminderSent(existing.id) }
    }

    @Test
    fun publishesOverdueEventForEachMarkedInvoice() {
        val existing = invoice()
        val envelope = slot<EventEnvelope<*>>()
        val repo = mockk<InvoiceRepository>()
        val publisher = mockk<DomainEventPublisher>()
        every { repo.markOverdue(LocalDate.now()) } returns listOf(existing)
        every { publisher.publish(capture(envelope)) } just runs

        MarkOverdueInvoicesUseCase(repo, publisher).execute()

        assertEquals("payment.overdue", envelope.captured.eventType)
        @Suppress("UNCHECKED_CAST")
        val payload = envelope.captured.payload as Map<String, Any>
        assertEquals(existing.id.value, payload["invoiceId"])
        assertEquals(existing.studentId, payload["studentId"])
        assertEquals(existing.dueDate, payload["dueDate"])
    }

    private fun invoice(
        amount: BigDecimal = BigDecimal("100.00"),
        status: InvoiceStatus = InvoiceStatus.PENDING,
    ) = Invoice(
        id = InvoiceId(UUID.fromString("00000000-0000-0000-0000-00000000b010")),
        studentId = UUID.fromString("00000000-0000-0000-0000-00000000b001"),
        enrollmentId = null,
        amount = amount,
        dueDate = LocalDate.of(2026, 11, 5),
        status = status,
    )
}
