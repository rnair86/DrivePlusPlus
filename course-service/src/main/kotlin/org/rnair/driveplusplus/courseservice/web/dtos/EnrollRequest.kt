package org.rnair.driveplusplus.courseservice.web.dtos

import java.util.UUID

data class EnrollRequest(
    val studentId: UUID,
    val batchId: UUID,
)
