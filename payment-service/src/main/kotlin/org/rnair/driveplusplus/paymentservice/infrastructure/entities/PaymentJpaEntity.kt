package org.rnair.driveplusplus.paymentservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payments")
class PaymentJpaEntity : BaseJpaEntity() {
    @Column(name = "invoice_id")
    lateinit var invoiceId: UUID

    var amount: BigDecimal = BigDecimal.ZERO

    @Column(name = "paid_at")
    lateinit var paidAt: Instant

    lateinit var method: String
}
