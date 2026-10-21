package org.rnair.driveplusplus.testservice.infrastructure.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.testservice.core.models.TestResult
import org.rnair.driveplusplus.testservice.core.models.TestSchedule
import org.rnair.driveplusplus.testservice.core.models.TestScheduleId
import org.rnair.driveplusplus.testservice.core.models.TestScheduleStatus
import org.rnair.driveplusplus.testservice.core.repository.TestScheduleRepository
import org.rnair.driveplusplus.testservice.infrastructure.entities.TestScheduleJpaEntity
import org.rnair.driveplusplus.testservice.infrastructure.mappers.TestScheduleMapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ApplicationScoped
class PanacheTestScheduleRepository :
    TestScheduleRepository,
    PanacheRepositoryBase<TestScheduleJpaEntity, UUID> {
    override fun save(test: TestSchedule): TestSchedule {
        val entity = TestScheduleMapper.toEntity(test)
        val managed =
            if (findById(entity.id) == null) {
                persist(entity)
                entity
            } else {
                getEntityManager().merge(entity)
            }
        return TestScheduleMapper.toDomain(managed)
    }

    override fun findByTestScheduleId(id: TestScheduleId) = findById(id.value)?.let(TestScheduleMapper::toDomain)

    override fun listByStudent(studentId: UUID) = find("studentId", studentId).list().map(TestScheduleMapper::toDomain)

    override fun findStartingWithinWithNoReminderSent(withinHours: Long): List<TestSchedule> {
        val cutoff = Instant.now().plus(withinHours, ChronoUnit.HOURS)
        return find("scheduledAt <= ?1 and reminderSentAt is null and status = ?2", cutoff, TestScheduleStatus.SCHEDULED)
            .list()
            .map(TestScheduleMapper::toDomain)
    }

    override fun markReminderSent(id: TestScheduleId) {
        update("reminderSentAt = ?1 where id = ?2", Instant.now(), id.value)
    }

    override fun updateStatus(
        id: TestScheduleId,
        status: TestScheduleStatus,
    ) {
        update("status = ?1 where id = ?2", status, id.value)
    }

    override fun recordResult(
        id: TestScheduleId,
        result: TestResult,
        notes: String?,
    ) {
        update(
            "result = ?1, resultNotes = ?2, status = ?3 where id = ?4",
            result,
            notes,
            TestScheduleStatus.COMPLETED,
            id.value,
        )
    }
}
