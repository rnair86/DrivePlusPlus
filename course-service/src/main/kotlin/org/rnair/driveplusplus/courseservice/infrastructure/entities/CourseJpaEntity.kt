package org.rnair.driveplusplus.courseservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.rnair.driveplusplus.courseservice.core.models.CourseType
import java.math.BigDecimal

@Entity
@Table(name = "courses")
class CourseJpaEntity : BaseJpaEntity() {
    lateinit var title: String
    lateinit var description: String

    @Enumerated(EnumType.STRING)
    @Column(name = "course_type")
    lateinit var courseType: CourseType

    @Column(name = "duration_hours")
    var durationHours: Int = 0
    var price: BigDecimal = BigDecimal.ZERO
    var active: Boolean = true
}
