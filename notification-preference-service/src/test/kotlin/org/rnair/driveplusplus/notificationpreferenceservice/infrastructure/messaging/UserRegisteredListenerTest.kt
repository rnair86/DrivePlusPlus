package org.rnair.driveplusplus.notificationpreferenceservice.infrastructure.messaging

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.notificationpreferenceservice.application.usecase.SeedDefaultPreferencesUseCase
import java.time.Instant
import java.util.UUID

class UserRegisteredListenerTest {
    @Test
    fun forwardsStudentAndInstructorRegistrations() {
        val seed = mockk<SeedDefaultPreferencesUseCase>()
        val studentId = UUID.fromString("00000000-0000-0000-0000-00000000c101")
        val instructorId = UUID.fromString("00000000-0000-0000-0000-00000000c102")
        every { seed.execute(any(), any()) } just runs
        val listener = UserRegisteredListener(seed)

        listener.onMessage(envelope("user.student.registered", UserRegisteredPayload(studentId = studentId, email = "a@b.c")))
        listener.onMessage(envelope("user.instructor.registered", UserRegisteredPayload(instructorId = instructorId, email = "c@d.e")))

        verify(exactly = 1) { seed.execute(studentId, "STUDENT") }
        verify(exactly = 1) { seed.execute(instructorId, "INSTRUCTOR") }
    }

    private fun envelope(
        eventType: String,
        payload: UserRegisteredPayload,
    ) = UserRegisteredEnvelope(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = Instant.parse("2026-10-22T12:00:00Z"),
        version = "1.0",
        source = "user-service",
        payload = payload,
    )
}
