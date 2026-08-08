package com.rnair.drivingplusplus.commons.error

import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper

// Shared JAX-RS mapper base — every service registers a 3-line subclass as its @Provider
abstract class BaseExceptionMapper : ExceptionMapper<RuntimeException> {
    override fun toResponse(e: RuntimeException): Response =
        when (e) {
            is NotFoundException -> problem(404, e.message)
            is ConflictException -> problem(409, e.message)
            is ValidationException -> problem(422, e.message)
            else -> problem(500, "Unexpected error")
        }

    private fun problem(
        status: Int,
        detail: String?,
    ): Response = Response.status(status).entity(ProblemDetail(title = "Error", status = status, detail = detail)).build()
}
