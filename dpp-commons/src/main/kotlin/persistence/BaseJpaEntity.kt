package com.rnair.drivingplusplus.commons.persistence

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.util.UUID

@MappedSuperclass
abstract class BaseJpaEntity : PanacheEntityBase {
    @Id lateinit var id: UUID
}
