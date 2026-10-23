package org.rnair.driveplusplus.notificationservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "templates")
class TemplateJpaEntity : BaseJpaEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    lateinit var eventType: NotificationEventType

    @Enumerated(EnumType.STRING)
    lateinit var channel: NotificationChannel

    @Column(name = "subject_template")
    var subjectTemplate: String? = null

    @Column(name = "body_template")
    lateinit var bodyTemplate: String
}
