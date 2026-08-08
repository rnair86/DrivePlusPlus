package com.rnair.drivingplusplus.commons.error

data class ProblemDetail(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<FieldError> = emptyList(),
)

data class FieldError(
    val field: String,
    val message: String,
)
