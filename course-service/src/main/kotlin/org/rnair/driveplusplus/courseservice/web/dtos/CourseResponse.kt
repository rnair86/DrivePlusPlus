package org.rnair.driveplusplus.courseservice.web.dtos

import java.math.BigDecimal
import java.util.UUID

data class CourseResponse(
    val id: UUID,
    val title: String,
    val description: String,
    val courseType: String,
    val durationHours: Int,
    val price: BigDecimal,
    val active: Boolean,
)
