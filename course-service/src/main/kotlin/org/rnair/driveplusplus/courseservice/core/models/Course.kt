package org.rnair.driveplusplus.courseservice.core.models

import java.math.BigDecimal

data class Course(
    val id: CourseId,
    val title: String,
    val description: String,
    val courseType: CourseType,
    val durationHours: Int,
    val price: BigDecimal,
    val active: Boolean = true,
)
