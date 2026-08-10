package org.rnair.driveplusplus.userservice.infrastructure.messaging

import com.rnair.drivingplusplus.commons.event.EventEnvelope
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RabbitMqEventPublisherTest {
    @Test
    fun publishesSingleEventEnvelopeWithCorrectRoutingKey() {
        val emitter = mockk<Emitter<EventEnvelope<*>>>()
        val messageSlot = slot<Message<EventEnvelope<*>>>()

        every { emitter.send(capture(messageSlot)) } returns mockk()

        val publisher = RabbitMqEventPublisher(emitter)
        val payload = "Test Event Payload"
        val eventType = "user.registered"
        val eventEnvelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = eventType,
            occurredAt = Instant.now(),
            version = "1.0",
            source = "user-service",
            correlationId = "corr-123",
            payload = payload,
        )

        publisher.publish(eventEnvelope)

        verify(exactly = 1) { emitter.send(any()) }
        val sentMessage = messageSlot.captured
        assertEquals(eventEnvelope, sentMessage.payload)
    }

    @Test
    fun setsRabbitMqRoutingKeyFromEventType() {
        val emitter = mockk<Emitter<EventEnvelope<*>>>()
        val messageSlot = slot<Message<EventEnvelope<*>>>()

        every { emitter.send(capture(messageSlot)) } returns mockk()

        val publisher = RabbitMqEventPublisher(emitter)
        val eventType = "student.registered"
        val eventEnvelope = EventEnvelope(
            eventType = eventType,
            source = "user-service",
            payload = mapOf("studentId" to "123"),
        )

        publisher.publish(eventEnvelope)

        val sentMessage = messageSlot.captured
        val metadata = sentMessage.metadata
            .get(OutgoingRabbitMQMetadata::class.java)

        assertNotNull(metadata)
        assertEquals(eventType, metadata.get().routingKey)
    }

    @Test
    fun publishesMultipleEventEnvelopes() {
        val emitter = mockk<Emitter<EventEnvelope<*>>>()

        every { emitter.send(any()) } returns mockk()

        val publisher = RabbitMqEventPublisher(emitter)

        val envelopes = listOf(
            EventEnvelope(
                eventType = "user.created",
                source = "user-service",
                payload = "event1",
            ),
            EventEnvelope(
                eventType = "user.updated",
                source = "user-service",
                payload = "event2",
            ),
            EventEnvelope(
                eventType = "user.deleted",
                source = "user-service",
                payload = "event3",
            ),
        )

        envelopes.forEach { publisher.publish(it) }

        verify(exactly = 3) { emitter.send(any()) }
    }

    @Test
    fun maintainsEventEnvelopePayloadStructure() {
        val emitter = mockk<Emitter<EventEnvelope<*>>>()
        val messageSlot = slot<Message<EventEnvelope<*>>>()

        every { emitter.send(capture(messageSlot)) } returns mockk()

        val publisher = RabbitMqEventPublisher(emitter)
        val customPayload = mapOf(
            "studentId" to "uuid-123",
            "email" to "student@example.com",
            "firstName" to "John",
            "lastName" to "Doe",
        )
        val eventEnvelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "student.profile.updated",
            occurredAt = Instant.now(),
            version = "1.0",
            source = "user-service",
            correlationId = "corr-456",
            payload = customPayload,
        )

        publisher.publish(eventEnvelope)

        val sentMessage = messageSlot.captured
        val sentEnvelope = sentMessage.payload

        assertEquals(eventEnvelope.eventId, sentEnvelope.eventId)
        assertEquals(eventEnvelope.eventType, sentEnvelope.eventType)
        assertEquals(eventEnvelope.payload, sentEnvelope.payload)
    }

    @Test
    fun handlesEventTypeWithDotNotation() {
        val emitter = mockk<Emitter<EventEnvelope<*>>>()
        val messageSlot = slot<Message<EventEnvelope<*>>>()

        every { emitter.send(capture(messageSlot)) } returns mockk()

        val publisher = RabbitMqEventPublisher(emitter)
        val eventType = "instructor.license.verified.event"
        val eventEnvelope = EventEnvelope(
            eventType = eventType,
            source = "user-service",
            payload = Unit,
        )

        publisher.publish(eventEnvelope)

        val sentMessage = messageSlot.captured
        val metadata = sentMessage.metadata
            .get(OutgoingRabbitMQMetadata::class.java)

        assertEquals(eventType, metadata.get().routingKey)
    }

    @Test
    fun preservesEventMetadataWhenPublishing() {
        val emitter = mockk<Emitter<EventEnvelope<*>>>()
        val messageSlot = slot<Message<EventEnvelope<*>>>()

        every { emitter.send(capture(messageSlot)) } returns mockk()

        val publisher = RabbitMqEventPublisher(emitter)
        val eventId = UUID.randomUUID()
        val occurredAt = Instant.parse("2026-08-08T10:30:00Z")
        val correlationId = "trace-789"
        val eventEnvelope = EventEnvelope(
            eventId = eventId,
            eventType = "test.event",
            occurredAt = occurredAt,
            version = "2.0",
            source = "test-source",
            correlationId = correlationId,
            payload = "test-payload",
        )

        publisher.publish(eventEnvelope)

        val sentMessage = messageSlot.captured
        val sentEnvelope = sentMessage.payload

        assertEquals(eventId, sentEnvelope.eventId)
        assertEquals(occurredAt, sentEnvelope.occurredAt)
        assertEquals("2.0", sentEnvelope.version)
        assertEquals("test-source", sentEnvelope.source)
        assertEquals(correlationId, sentEnvelope.correlationId)
    }

    @Test
    fun createsOutgoingRabbitMqMetadataForEachPublish() {
        val emitter = mockk<Emitter<EventEnvelope<*>>>()
        val messageSlot = slot<Message<EventEnvelope<*>>>()

        every { emitter.send(capture(messageSlot)) } returns mockk()

        val publisher = RabbitMqEventPublisher(emitter)
        val eventEnvelope = EventEnvelope(
            eventType = "metadata.test",
            source = "user-service",
            payload = "test",
        )

        publisher.publish(eventEnvelope)

        val sentMessage = messageSlot.captured
        val metadata = sentMessage.metadata
            .get(OutgoingRabbitMQMetadata::class.java)

        assertNotNull(metadata)
        assertEquals("metadata.test", metadata.get().routingKey)
    }
}

