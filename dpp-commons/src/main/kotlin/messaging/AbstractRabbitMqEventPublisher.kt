//package com.rnair.drivingplusplus.commons.messaging
//
//import com.rnair.drivingplusplus.commons.event.DomainEventPublisher
//import com.rnair.drivingplusplus.commons.event.EventEnvelope
//import org.eclipse.microprofile.reactive.messaging.Emitter
//import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata
//
//import org.eclipse.microprofile.reactive.messaging.Message
//
//abstract class AbstractRabbitMqEventPublisher(
//    private val emitter: Emitter<EventEnvelope<*>>
//): DomainEventPublisher {
//    override fun publish(envelope: EventEnvelope<*>) {
//        emitter.send(
//            Message.of(envelope).addMetadata(
//                OutgoingRabbitMQMetadata.builder().withRoutingKey(envelope.eventType)
//                    .build()
//            )
//        )
//    }
//}