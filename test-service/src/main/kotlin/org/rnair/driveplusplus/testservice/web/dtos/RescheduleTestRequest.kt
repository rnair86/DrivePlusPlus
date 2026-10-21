package org.rnair.driveplusplus.testservice.web.dtos

import java.time.Instant

data class RescheduleTestRequest(
    val scheduledAt: Instant,
)
