package org.rnair.driveplusplus.paymentservice.infrastructure.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import org.rnair.driveplusplus.paymentservice.core.models.Invoice
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceId
import org.rnair.driveplusplus.paymentservice.core.models.InvoiceStatus
import org.rnair.driveplusplus.paymentservice.core.repository.InvoiceRepository
import org.rnair.driveplusplus.paymentservice.infrastructure.entities.InvoiceJpaEntity
import org.rnair.driveplusplus.paymentservice.infrastructure.mappers.InvoiceMapper
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@ApplicationScoped
class PanacheInvoiceRepository :
    InvoiceRepository,
    PanacheRepositoryBase<InvoiceJpaEntity, UUID> {
    override fun save(invoice: Invoice): Invoice {
        val entity = InvoiceMapper.toEntity(invoice)
        val managed =
            if (findById(entity.id) == null) {
                persist(entity)
                entity
            } else {
                getEntityManager().merge(entity)
            }
        return InvoiceMapper.toDomain(managed)
    }

    override fun findByInvoiceId(id: InvoiceId) = findById(id.value)?.let(InvoiceMapper::toDomain)

    override fun findByIdForUpdate(id: InvoiceId): Invoice? =
        find("id", id.value)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResult()
            ?.let(InvoiceMapper::toDomain)

    override fun listByStudent(studentId: UUID) = find("studentId", studentId).list().map(InvoiceMapper::toDomain)

    override fun findDueReminders(withinDays: Long): List<Invoice> {
        val cutoff = LocalDate.now().plusDays(withinDays)
        return find("dueDate <= ?1 and reminderSentAt is null and status = ?2", cutoff, InvoiceStatus.PENDING)
            .list()
            .map(InvoiceMapper::toDomain)
    }

    override fun markReminderSent(id: InvoiceId) {
        update("reminderSentAt = ?1 where id = ?2", Instant.now(), id.value)
    }

    override fun markOverdue(asOf: LocalDate): List<Invoice> {
        val overdue =
            find("dueDate < ?1 and status = ?2", asOf, InvoiceStatus.PENDING)
                .list()
                .map(InvoiceMapper::toDomain)
        update("status = ?1 where dueDate < ?2 and status = ?3", InvoiceStatus.OVERDUE, asOf, InvoiceStatus.PENDING)
        return overdue
    }

    override fun updateStatus(
        id: InvoiceId,
        status: InvoiceStatus,
    ) {
        update("status = ?1 where id = ?2", status, id.value)
    }
}
