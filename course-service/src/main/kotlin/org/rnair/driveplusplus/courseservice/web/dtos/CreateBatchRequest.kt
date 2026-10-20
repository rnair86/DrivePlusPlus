package org.rnair.driveplusplus.courseservice.web.dtos

import java.time.LocalDate
import java.util.UUID

data class CreateBatchRequest(
    val courseId: UUID,
    val instructorId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val capacity: Int,
)
