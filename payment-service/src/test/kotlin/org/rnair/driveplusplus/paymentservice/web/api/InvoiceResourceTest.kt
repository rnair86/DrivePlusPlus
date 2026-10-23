package org.rnair.driveplusplus.paymentservice.web.api

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.paymentservice.application.usecase.CancelInvoiceUseCase
import org.rnair.driveplusplus.paymentservice.application.usecase.CreateInvoiceUseCase
import org.rnair.driveplusplus.paymentservice.application.usecase.ListInvoicesForStudentUseCase
import org.rnair.driveplusplus.paymentservice.application.usecase.RecordPaymentUseCase
import org.rnair.driveplusplus.paymentservice.core.exceptions.InvoiceAlreadyPaidException
import org.rnair.driveplusplus.paymentservice.core.exceptions.OverpaymentException
import org.rnair.driveplusplus.paymentservice.core.models.Invoice
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId
import org.rnair.driveplusplus.paymentservice.core.models.Payment
import org.rnair.driveplusplus.paymentservice.core.models.PaymentId
import org.rnair.driveplusplus.paymentservice.web.dtos.CreateInvoiceRequest
import org.rnair.driveplusplus.paymentservice.web.dtos.RecordPaymentRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class InvoiceResourceTest {
    @Test
    fun createsInvoiceAndReturnsCreatedStatus() {
        val create = mockk<CreateInvoiceUseCase>()
        val studentId = UUID.fromString("00000000-0000-0000-0000-00000000b201")
        val enrollmentId = UUID.fromString("00000000-0000-0000-0000-00000000b202")
        val dueDate = LocalDate.of(2026, 11, 5)
        every { create.execute(any(), any(), any(), any()) } returns invoice(studentId, enrollmentId, dueDate)

        val response =
            resource(create = create).create(CreateInvoiceRequest(studentId, enrollmentId, BigDecimal("150.00"), dueDate))

        assertEquals(201, response.status)
        verify(exactly = 1) { create.execute(studentId, enrollmentId, BigDecimal("150.00"), dueDate) }
    }

    @Test
    fun listsInvoicesForStudent() {
        val list = mockk<ListInvoicesForStudentUseCase>()
        val studentId = UUID.randomUUID()
        every { list.execute(studentId) } returns listOf(invoice(studentId, null, LocalDate.of(2026, 11, 5)))

        val result = resource(list = list).list(studentId)

        assertEquals(studentId, result.single().studentId)
        assertEquals("PENDING", result.single().status)
    }

    @Test
    fun cancelsInvoiceAndReturnsNoContent() {
        val cancel = mockk<CancelInvoiceUseCase>()
        val id = UUID.randomUUID()
        every { cancel.execute(InvoiceId(id)) } just runs

        val response = resource(cancel = cancel).cancel(id)

        assertEquals(204, response.status)
    }

    @Test
    fun recordsPaymentAndReturnsCreatedStatus() {
        val record = mockk<RecordPaymentUseCase>()
        val id = UUID.fromString("00000000-0000-0000-0000-00000000b210")
        every { record.execute(any(), any(), any()) } returns
            Payment(PaymentId(UUID.randomUUID()), InvoiceId(id), BigDecimal("25.00"), Instant.parse("2026-10-22T12:00:00Z"), "CARD")

        val response = resource(record = record).recordPayment(id, RecordPaymentRequest(BigDecimal("25.00"), "CARD"))

        assertEquals(201, response.status)
        verify(exactly = 1) { record.execute(InvoiceId(id), BigDecimal("25.00"), "CARD") }
    }

    @Test
    fun propagatesPaidAndOverpaymentConflicts() {
        val record = mockk<RecordPaymentUseCase>()
        val cancel = mockk<CancelInvoiceUseCase>()
        val id = UUID.randomUUID()
        every { record.execute(any(), any(), any()) } throws OverpaymentException(InvoiceId(id))
        every { cancel.execute(any()) } throws InvoiceAlreadyPaidException(InvoiceId(id))

        val resource = resource(cancel = cancel, record = record)
        assertThrows<OverpaymentException> { resource.recordPayment(id, RecordPaymentRequest(BigDecimal.TEN, "CASH")) }
        assertThrows<InvoiceAlreadyPaidException> { resource.cancel(id) }
    }

    private fun resource(
        create: CreateInvoiceUseCase = mockk(),
        list: ListInvoicesForStudentUseCase = mockk(),
        cancel: CancelInvoiceUseCase = mockk(),
        record: RecordPaymentUseCase = mockk(),
    ) = InvoiceResource(create, list, cancel, record)

    private fun invoice(
        studentId: UUID,
        enrollmentId: UUID?,
        dueDate: LocalDate,
    ) = Invoice(
        id = InvoiceId(UUID.fromString("00000000-0000-0000-0000-00000000b210")),
        studentId = studentId,
        enrollmentId = enrollmentId,
        amount = BigDecimal("150.00"),
        dueDate = dueDate,
    )
}
