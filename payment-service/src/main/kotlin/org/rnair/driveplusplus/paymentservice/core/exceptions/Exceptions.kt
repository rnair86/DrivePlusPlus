package org.rnair.driveplusplus.paymentservice.core.exceptions

import com.rnair.drivingplusplus.commons.error.ConflictException
import com.rnair.drivingplusplus.commons.error.NotFoundException
import com.rnair.drivingplusplus.commons.error.ValidationException
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId

class InvoiceNotFoundException(
    id: InvoiceId,
) : NotFoundException("Invoice not found: ${id.value}")

class InvoiceAlreadyPaidException(
    id: InvoiceId,
) : ConflictException("Invoice already paid: ${id.value}")

class InvoiceCancelledException(
    id: InvoiceId,
) : ConflictException("Invoice is cancelled: ${id.value}")

class OverpaymentException(
    id: InvoiceId,
) : ValidationException("Payment exceeds remaining balance for invoice ${id.value}")
