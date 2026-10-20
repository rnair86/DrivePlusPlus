package org.rnair.driveplusplus.courseservice.core.exceptions

import com.rnair.drivingplusplus.commons.error.ConflictException
import com.rnair.drivingplusplus.commons.error.NotFoundException
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import org.rnair.driveplusplus.courseservice.core.models.EnrollmentId
import org.rnair.driveplusplus.courseservice.core.models.SessionId
import java.util.UUID

class CourseNotFoundException(
    id: CourseId,
) : NotFoundException("Course not found: ${id.value}")

class BatchNotFoundException(
    id: BatchId,
) : NotFoundException("Batch not found: ${id.value}")

class BatchAtCapacityException(
    id: BatchId,
) : ConflictException("Batch at capacity: ${id.value}")

class AlreadyEnrolledException(
    studentId: UUID,
    batchId: BatchId,
) : ConflictException("Student $studentId already enrolled in batch ${batchId.value}")

class EnrollmentNotFoundException(
    id: EnrollmentId,
) : NotFoundException("Enrollment not found: ${id.value}")

class SessionNotFoundException(
    id: SessionId,
) : NotFoundException("Session not found: ${id.value}")
