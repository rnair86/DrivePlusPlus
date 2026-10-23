package org.rnair.driveplusplus.notificationservice.infrastructure.messaging

import com.fasterxml.jackson.databind.JsonNode
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import org.rnair.driveplusplus.notificationservice.core.models.Recipient
import java.util.UUID

object DomainEventMapping {
    fun map(
        eventType: String,
        payload: JsonNode,
    ): Pair<NotificationEventType, List<Recipient>>? {
        val mapped =
            when (eventType) {
                "user.student.registered" -> {
                    typed(payload, NotificationEventType.STUDENT_REGISTERED, "studentId", useEmail = true)
                }

                "user.instructor.registered" -> {
                    typed(payload, NotificationEventType.INSTRUCTOR_REGISTERED, "instructorId", useEmail = true)
                }

                "appointment.created" -> {
                    typed(payload, NotificationEventType.APPOINTMENT_CREATED, "studentId", "instructorId")
                }

                "appointment.reminder.due" -> {
                    typed(payload, NotificationEventType.APPOINTMENT_REMINDER, "studentId", "instructorId")
                }

                "appointment.cancelled" -> {
                    typed(payload, NotificationEventType.APPOINTMENT_CANCELLED, "studentId")
                }

                "test.scheduled" -> {
                    typed(payload, NotificationEventType.TEST_SCHEDULED, "studentId")
                }

                "test.reminder.due" -> {
                    typed(payload, NotificationEventType.TEST_REMINDER, "studentId")
                }

                "test.result.published" -> {
                    typed(payload, NotificationEventType.TEST_RESULT_PUBLISHED, "studentId")
                }

                "payment.reminder.due" -> {
                    typed(payload, NotificationEventType.PAYMENT_DUE, "studentId")
                }

                "payment.overdue" -> {
                    typed(payload, NotificationEventType.PAYMENT_OVERDUE, "studentId")
                }

                "payment.received" -> {
                    typed(payload, NotificationEventType.PAYMENT_RECEIVED, "studentId")
                }

                else -> {
                    null
                }
            }
        return mapped?.takeIf { it.second.isNotEmpty() }
    }

    private fun typed(
        payload: JsonNode,
        eventType: NotificationEventType,
        vararg idFields: String,
        useEmail: Boolean = false,
    ): Pair<NotificationEventType, List<Recipient>> = eventType to idFields.mapNotNull { recipient(payload, it, useEmail) }

    private fun recipient(
        payload: JsonNode,
        idField: String,
        useEmail: Boolean,
    ): Recipient? {
        val userId = uuid(payload, idField) ?: return null
        val email = payload.path("email").asText("")
        val address = if (useEmail && email.isNotEmpty()) email else userId.toString()
        return Recipient(userId, address)
    }

    private fun uuid(
        payload: JsonNode,
        field: String,
    ): UUID? {
        val text = payload.path(field).asText("")
        if (text.isEmpty()) return null
        return runCatching { UUID.fromString(text) }.getOrNull()
    }
}
