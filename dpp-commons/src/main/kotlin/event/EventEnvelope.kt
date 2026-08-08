package com.rnair.drivingplusplus.commons.event

import java.time.Instant
import java.util.UUID

data class EventEnvelope<T>(
    val eventId: UUID = UUID.randomUUID(),
    val eventType: String,
    val occurredAt: Instant = Instant.now(),
    val version: String = "1.0",
    val source: String,
    val correlationId: String? = null,
    val payload: T,
)
