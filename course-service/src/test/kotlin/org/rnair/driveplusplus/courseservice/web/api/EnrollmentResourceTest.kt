package org.rnair.driveplusplus.courseservice.web.api

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rnair.driveplusplus.courseservice.application.usecase.EnrollStudentUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.ListEnrollmentsForStudentUseCase
import org.rnair.driveplusplus.courseservice.application.usecase.WithdrawEnrollmentUseCase
import org.rnair.driveplusplus.courseservice.core.exceptions.BatchAtCapacityException
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.Enrollment
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentId
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentStatus
import org.rnair.driveplusplus.courseservice.web.dtos.EnrollRequest
import java.time.Instant
import java.util.UUID

class EnrollmentResourceTest {
    @Test
    fun enrollsStudentAndReturnsCreatedStatus() {
        val enroll = mockk<EnrollStudentUseCase>()
        val studentId = UUID.fromString("00000000-0000-0000-0000-000000008001")
        val batchId = UUID.fromString("00000000-0000-0000-0000-000000008002")
        every { enroll.execute(any(), any()) } returns enrollment(studentId, BatchId(batchId))

        val response = resource(enroll = enroll).enroll(EnrollRequest(studentId, batchId))

        assertEquals(201, response.status)
        verify(exactly = 1) { enroll.execute(studentId, BatchId(batchId)) }
    }

    @Test
    fun propagatesCapacityConflict() {
        val enroll = mockk<EnrollStudentUseCase>()
        val batchId = UUID.randomUUID()
        every { enroll.execute(any(), any()) } throws BatchAtCapacityException(BatchId(batchId))

        assertThrows<BatchAtCapacityException> {
            resource(enroll = enroll).enroll(EnrollRequest(UUID.randomUUID(), batchId))
        }
    }

    @Test
    fun listsEnrollmentsForStudent() {
        val list = mockk<ListEnrollmentsForStudentUseCase>()
        val studentId = UUID.randomUUID()
        every { list.execute(studentId) } returns listOf(enrollment(studentId, BatchId(UUID.randomUUID())))

        val result = resource(list = list).list(studentId)

        assertEquals(studentId, result.single().studentId)
        assertEquals("ACTIVE", result.single().status)
    }

    @Test
    fun withdrawsEnrollmentAndReturnsNoContent() {
        val withdraw = mockk<WithdrawEnrollmentUseCase>()
        val id = UUID.randomUUID()
        every { withdraw.execute(EnrollmentId(id)) } just runs

        val response = resource(withdraw = withdraw).withdraw(id)

        assertEquals(204, response.status)
        verify(exactly = 1) { withdraw.execute(EnrollmentId(id)) }
    }

    private fun resource(
        enroll: EnrollStudentUseCase = mockk(),
        list: ListEnrollmentsForStudentUseCase = mockk(),
        withdraw: WithdrawEnrollmentUseCase = mockk(),
    ) = EnrollmentResource(enroll, list, withdraw)

    private fun enrollment(
        studentId: UUID,
        batchId: BatchId,
    ) = Enrollment(
        id = EnrollmentId(UUID.fromString("00000000-0000-0000-0000-000000008010")),
        studentId = studentId,
        batchId = batchId,
        enrolledAt = Instant.parse("2026-10-20T12:00:00Z"),
        status = EnrollmentStatus.ACTIVE,
    )
}
