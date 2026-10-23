package org.rnair.driveplusplus.notificationservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import com.rnair.drivingplusplus.commons.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.rnair.driveplusplus.notificationservice.core.models.DeliveryStatus
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notification_log")
class NotificationLogJpaEntity : BaseJpaEntity() {
    @Column(name = "user_id")
    lateinit var userId: UUID

    @Column(name = "source_event_id")
    lateinit var sourceEventId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    lateinit var eventType: NotificationEventType

    @Enumerated(EnumType.STRING)
    lateinit var channel: NotificationChannel

    @Enumerated(EnumType.STRING)
    lateinit var status: DeliveryStatus

    @Column(name = "provider_message_id")
    var providerMessageId: String? = null

    @Column(name = "sent_at")
    lateinit var sentAt: Instant

    @Column(name = "error_message")
    var errorMessage: String? = null
}
