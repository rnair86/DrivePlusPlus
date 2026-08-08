package org.rnair.driveplusplus.userservice.web.dtos

import org.rnair.driveplusplus.userservice.core.model.AccountStatus

data class UpdateStatusRequest(
    val status: AccountStatus,
)
