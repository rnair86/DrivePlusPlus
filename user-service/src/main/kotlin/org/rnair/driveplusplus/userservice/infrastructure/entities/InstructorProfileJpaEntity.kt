package org.rnair.driveplusplus.userservice.infrastructure.entities

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "instructor_profiles")
class InstructorProfileJpaEntity : PanacheEntityBase {
    @Id
    @Column(name = "user_id")
    lateinit var userId: UUID

    @Column(name = "first_name")
    lateinit var firstName: String

    @Column(name = "last_name")
    lateinit var lastName: String

    @Column(name = "phone")
    var phone: String? = null

    @Column(name = "license_cert_number")
    var licenseCertNumber: String? = null

    @Column(name = "years_experience")
    var yearsExperience: Int = 0

    @ElementCollection
    @CollectionTable(name = "instructor_specialties", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "specialty")
    var specialties: MutableList<String> = mutableListOf()

    @Column(name = "vehicle_assigned")
    var vehicleAssigned: String? = null
}
