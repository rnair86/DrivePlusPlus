package com.rnair.drivingplusplus.commons.event

interface DomainEventPublisher {
    fun publish(envelope: EventEnvelope<*>)
}
