package org.rnair.driveplusplus.courseservice.core.models

import java.util.UUID

@JvmInline value class CourseId(
    val value: UUID,
)

@JvmInline value class BatchId(
    val value: UUID,
)

@JvmInline value class SessionId(
    val value: UUID,
)

@JvmInline value class EnrollmentId(
    val value: UUID,
)
