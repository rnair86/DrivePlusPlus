package org.rnair.driveplusplus.courseservice.infrastructure.messaging

import com.rnair.drivingplusplus.commons.event.EventEnvelope
import com.rnair.drivingplusplus.commons.messaging.AbstractRabbitMqEventPublisher
import jakarta.inject.Singleton
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter

@Singleton
class RabbitMqEventPublisher(
    @Channel("dpp-events-out") emitter: Emitter<EventEnvelope<*>>,
) : AbstractRabbitMqEventPublisher(emitter)
