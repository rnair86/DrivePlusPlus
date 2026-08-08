package com.rnair.drivingplusplus.commons.error

// Every service-specific exception extends one of these three, never plain RuntimeException
abstract class DomainException(
    message: String,
) : RuntimeException(message)

abstract class NotFoundException(
    message: String,
) : DomainException(message)

abstract class ConflictException(
    message: String,
) : DomainException(message)

abstract class ValidationException(
    message: String,
) : DomainException(message)
