package org.rnair.driveplusplus.userservice.core.exceptions

import com.rnair.drivingplusplus.commons.error.ConflictException
import com.rnair.drivingplusplus.commons.error.NotFoundException
import com.rnair.drivingplusplus.commons.error.ValidationException
import org.rnair.driveplusplus.userservice.core.model.UserId

class EmailAlreadyRegisteredException(
    email: String,
) : ConflictException("Email already registered: $email")

class MinorMissingGuardianContactException : ValidationException("Guardian contact is required for students under 18")

class UserNotFoundException(
    id: UserId,
) : NotFoundException("User not found: ${id.value}")

class InvalidCredentialsException : ValidationException("Invalid email or password")
