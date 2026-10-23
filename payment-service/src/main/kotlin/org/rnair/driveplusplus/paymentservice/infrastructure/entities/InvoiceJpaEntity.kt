package org.rnair.driveplusplus.paymentservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "invoices")
class InvoiceJpaEntity : BaseJpaEntity() {
    @Column(name = "student_id")
    lateinit var studentId: UUID

    @Column(name = "enrollment_id")
    var enrollmentId: UUID? = null

    var amount: BigDecimal = BigDecimal.ZERO

    @Column(name = "due_date")
    lateinit var dueDate: LocalDate

    @Enumerated(EnumType.STRING)
    var status: InvoiceStatus = InvoiceStatus.PENDING

    @Column(name = "reminder_sent_at")
    var reminderSentAt: Instant? = null
}
