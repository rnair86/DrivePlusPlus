package org.rnair.driveplusplus.testservice.core.exceptions

import com.rnair.drivingplusplus.commons.error.ConflictException
import com.rnair.drivingplusplus.commons.error.NotFoundException
import org.rnair.driveplusplus.testservice.core.models.TestScheduleId

class TestScheduleNotFoundException(
    id: TestScheduleId,
) : NotFoundException("Test schedule not found: ${id.value}")

class TestAlreadyCompletedException(
    id: TestScheduleId,
) : ConflictException("Test already completed: ${id.value}")

class TestAlreadyCancelledException(
    id: TestScheduleId,
) : ConflictException("Test already cancelled: ${id.value}")
