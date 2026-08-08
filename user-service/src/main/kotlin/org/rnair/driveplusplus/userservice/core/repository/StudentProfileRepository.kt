package org.rnair.driveplusplus.userservice.core.repository

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import org.rnair.driveplusplus.userservice.core.model.StudentProfile
import org.rnair.driveplusplus.userservice.core.model.UserId

interface StudentProfileRepository {
    fun save(profile: StudentProfile): StudentProfile

    fun findByUserId(id: UserId): StudentProfile?

    fun search(
        query: String?,
        page: PageRequest,
    ): PageResult<StudentProfile>
}
