package org.rnair.driveplusplus.paymentservice.web.api

import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import org.rnair.driveplusplus.paymentservice.application.usecase.CancelInvoiceUseCase
import org.rnair.driveplusplus.paymentservice.application.usecase.CreateInvoiceUseCase
import org.rnair.driveplusplus.paymentservice.application.usecase.ListInvoicesForStudentUseCase
import org.rnair.driveplusplus.paymentservice.application.usecase.RecordPaymentUseCase
import org.rnair.driveplusplus.paymentservice.core.models.Invoice
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId
import org.rnair.driveplusplus.paymentservice.core.models.Payment
import org.rnair.driveplusplus.paymentservice.web.dtos.CreateInvoiceRequest
import org.rnair.driveplusplus.paymentservice.web.dtos.InvoiceResponse
import org.rnair.driveplusplus.paymentservice.web.dtos.PaymentResponse
import org.rnair.driveplusplus.paymentservice.web.dtos.RecordPaymentRequest
import java.util.UUID

@Path("/api/v1/invoices")
class InvoiceResource(
    private val createInvoiceUseCase: CreateInvoiceUseCase,
    private val listInvoicesForStudentUseCase: ListInvoicesForStudentUseCase,
    private val cancelInvoiceUseCase: CancelInvoiceUseCase,
    private val recordPaymentUseCase: RecordPaymentUseCase,
) {
    @POST
    @RunOnVirtualThread
    fun create(request: CreateInvoiceRequest): Response {
        val invoice =
            createInvoiceUseCase.execute(
                request.studentId,
                request.enrollmentId,
                request.amount,
                request.dueDate,
            )
        return Response.status(201).entity(toResponse(invoice)).build()
    }

    @GET
    @RunOnVirtualThread
    fun list(
        @QueryParam("studentId") studentId: UUID,
    ): List<InvoiceResponse> = listInvoicesForStudentUseCase.execute(studentId).map(::toResponse)

    @DELETE
    @Path("/{id}")
    @RunOnVirtualThread
    fun cancel(
        @PathParam("id") id: UUID,
    ): Response {
        cancelInvoiceUseCase.execute(InvoiceId(id))
        return Response.noContent().build()
    }

    @POST
    @Path("/{id}/payments")
    @RunOnVirtualThread
    fun recordPayment(
        @PathParam("id") id: UUID,
        request: RecordPaymentRequest,
    ): Response {
        val payment = recordPaymentUseCase.execute(InvoiceId(id), request.amount, request.method)
        return Response.status(201).entity(toResponse(payment)).build()
    }

    private fun toResponse(invoice: Invoice) =
        InvoiceResponse(
            invoice.id.value,
            invoice.studentId,
            invoice.enrollmentId,
            invoice.amount,
            invoice.dueDate,
            invoice.status.name,
            invoice.reminderSentAt,
        )

    private fun toResponse(payment: Payment) =
        PaymentResponse(payment.id.value, payment.invoiceId.value, payment.amount, payment.paidAt, payment.method)
}
