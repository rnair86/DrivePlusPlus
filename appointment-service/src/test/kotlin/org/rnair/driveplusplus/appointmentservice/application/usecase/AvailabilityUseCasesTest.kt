package org.rnair.driveplusplus.appointmentservice.application.usecase

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.appointmentservice.core.models.AvailabilitySlot
import org.rnair.driveplusplus.appointmentservice.core.models.AvailabilitySlotId
import org.rnair.driveplusplus.appointmentservice.core.repository.AvailabilityRepository
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

class AvailabilityUseCasesTest {
    @Test
    fun definesAvailabilitySlot() {
        val instructorId = UUID.fromString("00000000-0000-0000-0000-000000009001")
        val repo = mockk<AvailabilityRepository>()
        every { repo.save(any()) } answers { firstArg() }

        val saved =
            DefineAvailabilityUseCase(repo).execute(
                instructorId,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
            )

        assertEquals(instructorId, saved.instructorId)
        assertEquals(DayOfWeek.MONDAY, saved.dayOfWeek)
        assertEquals(LocalTime.of(9, 0), saved.startTime)
        assertEquals(LocalTime.of(17, 0), saved.endTime)
        verify(exactly = 1) { repo.save(any()) }
    }

    @Test
    fun listsSlotsForInstructor() {
        val instructorId = UUID.randomUUID()
        val expected =
            listOf(
                AvailabilitySlot(
                    AvailabilitySlotId(UUID.randomUUID()),
                    instructorId,
                    DayOfWeek.TUESDAY,
                    LocalTime.of(10, 0),
                    LocalTime.of(12, 0),
                ),
            )
        val repo = mockk<AvailabilityRepository>()
        every { repo.findForInstructor(instructorId) } returns expected

        assertEquals(expected, GetAvailableSlotsUseCase(repo).execute(instructorId))
    }
}
