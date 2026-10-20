package org.rnair.driveplusplus.courseservice.core.repository

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import org.rnair.driveplusplus.courseservice.core.models.BatchId
import org.rnair.driveplusplus.courseservice.core.models.BatchStatus
import org.rnair.driveplusplus.courseservice.core.models.ClassBatch
import org.rnair.driveplusplus.courseservice.core.models.CourseId
import java.util.UUID

interface ClassBatchRepository {
    fun save(batch: ClassBatch): ClassBatch

    fun findByBatchId(id: BatchId): ClassBatch?

    fun findByIdForUpdate(id: BatchId): ClassBatch? // infra implements with SELECT ... FOR UPDATE

    fun list(
        courseId: CourseId?,
        instructorId: UUID?,
        status: BatchStatus?,
        page: PageRequest,
    ): PageResult<ClassBatch>
}
