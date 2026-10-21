package org.rnair.driveplusplus.appointmentservice.web.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.appointmentservice.application.usecase.DefineAvailabilityUseCase
import org.rnair.driveplusplus.appointmentservice.application.usecase.GetAvailableSlotsUseCase
import org.rnair.driveplusplus.appointmentservice.core.models.AvailabilitySlot
import org.rnair.driveplusplus.appointmentservice.core.models.AvailabilitySlotId
import org.rnair.driveplusplus.appointmentservice.web.dtos.DefineAvailabilityRequest
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

class AvailabilityResourceTest {
    @Test
    fun definesSlotAndReturnsCreatedStatus() {
        val define = mockk<DefineAvailabilityUseCase>()
        val instructorId = UUID.fromString("00000000-0000-0000-0000-000000009401")
        val request = DefineAvailabilityRequest(instructorId, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0))
        every { define.execute(any(), any(), any(), any()) } returns
            AvailabilitySlot(AvailabilitySlotId(UUID.randomUUID()), instructorId, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0))

        val response = AvailabilityResource(define, mockk()).define(request)

        assertEquals(201, response.status)
        verify(exactly = 1) { define.execute(instructorId, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)) }
    }

    @Test
    fun listsSlotsForInstructor() {
        val list = mockk<GetAvailableSlotsUseCase>()
        val instructorId = UUID.randomUUID()
        every { list.execute(instructorId) } returns
            listOf(
                AvailabilitySlot(
                    AvailabilitySlotId(UUID.randomUUID()),
                    instructorId,
                    DayOfWeek.FRIDAY,
                    LocalTime.of(13, 0),
                    LocalTime.of(15, 0),
                ),
            )

        val result = AvailabilityResource(mockk(), list).list(instructorId)

        assertEquals("FRIDAY", result.single().dayOfWeek)
        assertEquals(instructorId, result.single().instructorId)
    }
}
