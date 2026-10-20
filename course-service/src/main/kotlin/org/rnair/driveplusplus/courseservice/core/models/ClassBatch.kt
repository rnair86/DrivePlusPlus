package org.rnair.driveplusplus.courseservice.core.models

import java.time.LocalDate
import java.util.UUID

data class ClassBatch(
    val id: BatchId,
    val courseId: CourseId,
    val instructorId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val capacity: Int = 20,
    val status: BatchStatus = BatchStatus.SCHEDULED,
)
