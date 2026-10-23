package org.rnair.driveplusplus.paymentservice.application.usecase

import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
import com.rnair.drivingplusplus.commons.event.EventEnvelope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.rnair.driveplusplus.paymentservice.core.exceptions.InvoiceAlreadyPaidException
import org.rnair.driveplusplus.paymentservice.core.exceptions.InvoiceCancelledException
import org.rnair.driveplusplus.paymentservice.core.exceptions.InvoiceNotFoundException
import org.rnair.driveplusplus.paymentservice.core.exceptions.OverpaymentException
import org.rnair.driveplusplus.paymentservice.core.models.Invoice
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceStatus
import org.rnair.driveplusplus.paymentservice.core.models.Payment
import org.rnair.driveplusplus.paymentservice.core.models.PaymentId
import org.rnair.driveplusplus.paymentservice.core.repository.InvoiceRepository
import org.rnair.driveplusplus.paymentservice.core.repository.PaymentRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class InvoiceCreatedPayload(
    val invoiceId: UUID,
    val studentId: UUID,
    val amount: BigDecimal,
    val dueDate: LocalDate,
)

data class EnrollmentCreatedPayload(
    val enrollmentId: UUID,
    val studentId: UUID,
    val batchId: UUID,
    val courseId: UUID,
    val coursePrice: BigDecimal,
)

data class PaymentReceivedPayload(
    val invoiceId: UUID,
    val studentId: UUID,
    val amount: BigDecimal,
    val paidAt: Instant,
)

data class PaymentReminderDuePayload(
    val invoiceId: UUID,
    val studentId: UUID,
    val amount: BigDecimal,
    val dueDate: LocalDate,
)

@ApplicationScoped
class CreateInvoiceUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        studentId: UUID,
        enrollmentId: UUID?,
        amount: BigDecimal,
        dueDate: LocalDate,
    ): Invoice {
        val invoice =
            invoiceRepository.save(
                Invoice(InvoiceId(UUID.randomUUID()), studentId, enrollmentId, amount, dueDate),
            )
        eventPublisher.publish(
            EventEnvelope(
                eventType = "payment.invoice.created",
                source = "payment-service",
                payload = InvoiceCreatedPayload(invoice.id.value, studentId, amount, dueDate),
            ),
        )
        return invoice
    }
}

@ApplicationScoped
class GenerateInvoiceOnEnrollmentUseCase(
    private val createInvoiceUseCase: CreateInvoiceUseCase,
) {
    @Transactional
    fun execute(payload: EnrollmentCreatedPayload) {
        createInvoiceUseCase.execute(payload.studentId, payload.enrollmentId, payload.coursePrice, LocalDate.now().plusDays(14))
    }
}

@ApplicationScoped
class RecordPaymentUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: PaymentRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(
        invoiceId: InvoiceId,
        amount: BigDecimal,
        method: String,
    ): Payment {
        val invoice = invoiceRepository.findByIdForUpdate(invoiceId) ?: throw InvoiceNotFoundException(invoiceId)
        if (invoice.status == InvoiceStatus.CANCELLED) throw InvoiceCancelledException(invoiceId)
        if (invoice.status == InvoiceStatus.PAID) throw InvoiceAlreadyPaidException(invoiceId)

        val alreadyPaid = paymentRepository.sumPaidForInvoice(invoiceId)
        val newTotal = alreadyPaid.add(amount)
        if (newTotal.compareTo(invoice.amount) > 0) throw OverpaymentException(invoiceId)

        val payment = paymentRepository.save(Payment(PaymentId(UUID.randomUUID()), invoiceId, amount, Instant.now(), method))
        if (newTotal.compareTo(invoice.amount) == 0) invoiceRepository.updateStatus(invoiceId, InvoiceStatus.PAID)

        eventPublisher.publish(
            EventEnvelope(
                eventType = "payment.received",
                source = "payment-service",
                payload = PaymentReceivedPayload(invoiceId.value, invoice.studentId, amount, payment.paidAt),
            ),
        )
        return payment
    }
}

@ApplicationScoped
class CancelInvoiceUseCase(
    private val repo: InvoiceRepository,
) {
    @Transactional
    fun execute(id: InvoiceId) {
        val invoice = repo.findByInvoiceId(id) ?: throw InvoiceNotFoundException(id)
        when (invoice.status) {
            InvoiceStatus.PAID -> throw InvoiceAlreadyPaidException(id)
            InvoiceStatus.CANCELLED -> throw InvoiceCancelledException(id)
            InvoiceStatus.PENDING, InvoiceStatus.OVERDUE -> repo.updateStatus(id, InvoiceStatus.CANCELLED)
        }
    }
}

@ApplicationScoped
class ListInvoicesForStudentUseCase(
    private val repo: InvoiceRepository,
) {
    fun execute(studentId: UUID): List<Invoice> = repo.listByStudent(studentId)
}

@ApplicationScoped
class FindDueReminderInvoicesUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute() {
        invoiceRepository.findDueReminders(withinDays = 3).forEach { invoice ->
            eventPublisher.publish(
                EventEnvelope(
                    eventType = "payment.reminder.due",
                    source = "payment-service",
                    payload = PaymentReminderDuePayload(invoice.id.value, invoice.studentId, invoice.amount, invoice.dueDate),
                ),
            )
            invoiceRepository.markReminderSent(invoice.id)
        }
    }
}

@ApplicationScoped
class MarkOverdueInvoicesUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute() {
        invoiceRepository.markOverdue(LocalDate.now()).forEach { invoice ->
            eventPublisher.publish(
                EventEnvelope(
                    eventType = "payment.overdue",
                    source = "payment-service",
                    payload =
                        mapOf(
                            "invoiceId" to invoice.id.value,
                            "studentId" to invoice.studentId,
                            "amount" to invoice.amount,
                            "dueDate" to invoice.dueDate,
                        ),
                ),
            )
        }
    }
}
