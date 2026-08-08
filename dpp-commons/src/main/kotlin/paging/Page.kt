package com.rnair.drivingplusplus.commons.paging

data class PageRequest(
    val page: Int = 0,
    val size: Int = 20,
)

data class PageResult<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
)
