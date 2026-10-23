package org.rnair.driveplusplus.notificationservice.infrastructure.messaging

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rnair.driveplusplus.notificationservice.core.models.Recipient
import java.util.UUID

class DomainEventMappingTest {
    @Test
    fun mapsEachRoutedKey() {
        val studentId = UUID.fromString("00000000-0000-0000-0000-00000000d001")
        val instructorId = UUID.fromString("00000000-0000-0000-0000-00000000d002")

        assertEquals(
            NotificationEventType.STUDENT_REGISTERED to listOf(Recipient(studentId, "student@example.com")),
            DomainEventMapping.map("user.student.registered", payload("studentId" to studentId, "email" to "student@example.com")),
        )
        assertEquals(
            NotificationEventType.INSTRUCTOR_REGISTERED to listOf(Recipient(instructorId, "instructor@example.com")),
            DomainEventMapping.map(
                "user.instructor.registered",
                payload("instructorId" to instructorId, "email" to "instructor@example.com"),
            ),
        )
        assertEquals(
            NotificationEventType.APPOINTMENT_CREATED to
                listOf(Recipient(studentId, studentId.toString()), Recipient(instructorId, instructorId.toString())),
            DomainEventMapping.map("appointment.created", payload("studentId" to studentId, "instructorId" to instructorId)),
        )
        assertEquals(
            NotificationEventType.APPOINTMENT_REMINDER to
                listOf(Recipient(studentId, studentId.toString()), Recipient(instructorId, instructorId.toString())),
            DomainEventMapping.map("appointment.reminder.due", payload("studentId" to studentId, "instructorId" to instructorId)),
        )
        assertEquals(
            NotificationEventType.APPOINTMENT_CANCELLED to listOf(Recipient(studentId, studentId.toString())),
            DomainEventMapping.map("appointment.cancelled", payload("studentId" to studentId)),
        )
        assertEquals(
            NotificationEventType.TEST_SCHEDULED to listOf(Recipient(studentId, studentId.toString())),
            DomainEventMapping.map("test.scheduled", payload("studentId" to studentId)),
        )
        assertEquals(
            NotificationEventType.TEST_REMINDER to listOf(Recipient(studentId, studentId.toString())),
            DomainEventMapping.map("test.reminder.due", payload("studentId" to studentId)),
        )
        assertEquals(
            NotificationEventType.TEST_RESULT_PUBLISHED to listOf(Recipient(studentId, studentId.toString())),
            DomainEventMapping.map("test.result.published", payload("studentId" to studentId)),
        )
        assertEquals(
            NotificationEventType.PAYMENT_DUE to listOf(Recipient(studentId, studentId.toString())),
            DomainEventMapping.map("payment.reminder.due", payload("studentId" to studentId)),
        )
        assertEquals(
            NotificationEventType.PAYMENT_OVERDUE to listOf(Recipient(studentId, studentId.toString())),
            DomainEventMapping.map("payment.overdue", payload("studentId" to studentId)),
        )
        assertEquals(
            NotificationEventType.PAYMENT_RECEIVED to listOf(Recipient(studentId, studentId.toString())),
            DomainEventMapping.map("payment.received", payload("studentId" to studentId)),
        )
    }

    @Test
    fun unmappedKeysProduceNothing() {
        val userId = UUID.randomUUID()
        assertNull(DomainEventMapping.map("user.status.changed", payload("userId" to userId)))
        assertNull(DomainEventMapping.map("class.session.scheduled", payload("batchId" to UUID.randomUUID())))
        assertNull(DomainEventMapping.map("course.enrollment.created", payload("studentId" to userId)))
        assertNull(DomainEventMapping.map("appointment.rescheduled", payload("appointmentId" to UUID.randomUUID())))
        assertNull(DomainEventMapping.map("test.cancelled", payload("studentId" to userId)))
        assertNull(DomainEventMapping.map("payment.invoice.created", payload("studentId" to userId)))
    }

    private fun payload(vararg fields: Pair<String, Any>) =
        JsonNodeFactory.instance.objectNode().apply {
            fields.forEach { (name, value) -> put(name, value.toString()) }
        }
}
