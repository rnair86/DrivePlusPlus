package org.rnair.driveplusplus.userservice.core.repository

import com.rnair.drivingplusplus.commons.paging.PageRequest
import com.rnair.drivingplusplus.commons.paging.PageResult
import org.rnair.driveplusplus.userservice.core.model.InstructorProfile
import org.rnair.driveplusplus.userservice.core.model.UserId

interface InstructorProfileRepository {
    fun save(profile: InstructorProfile): InstructorProfile

    fun findByUserId(id: UserId): InstructorProfile?

    fun findAll(page: PageRequest): PageResult<InstructorProfile>
}
