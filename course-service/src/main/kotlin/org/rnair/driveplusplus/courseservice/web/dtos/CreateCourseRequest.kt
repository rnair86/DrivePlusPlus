package org.rnair.driveplusplus.courseservice.web.dtos

import org.rnair.driveplusplus.courseservice.core.models.CourseType
import java.math.BigDecimal

data class CreateCourseRequest(
    val title: String,
    val description: String,
    val courseType: CourseType,
    val durationHours: Int,
    val price: BigDecimal,
)
