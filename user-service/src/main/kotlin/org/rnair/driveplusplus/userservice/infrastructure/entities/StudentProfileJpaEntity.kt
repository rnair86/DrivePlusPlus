package org.rnair.driveplusplus.userservice.infrastructure.entities

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.rnair.driveplusplus.userservice.core.model.LicenseStage
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "student_profiles")
class StudentProfileJpaEntity : PanacheEntityBase {
    @Id
    @Column(name = "user_id")
    lateinit var userId: UUID

    @Column(name = "first_name")
    lateinit var firstName: String

    @Column(name = "last_name")
    lateinit var lastName: String

    @Column(name = "phone")
    var phone: String? = null

    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null

    @Column(name = "guardian_contact")
    var guardianContact: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "license_stage")
    var licenseStage: LicenseStage = LicenseStage.LEARNER
}
