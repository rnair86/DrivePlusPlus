package org.rnair.driveplusplus.testservice.web.dtos

import org.rnair.driveplusplus.testservice.core.models.TestResult

data class RecordTestResultRequest(
    val result: TestResult,
    val notes: String?,
)
