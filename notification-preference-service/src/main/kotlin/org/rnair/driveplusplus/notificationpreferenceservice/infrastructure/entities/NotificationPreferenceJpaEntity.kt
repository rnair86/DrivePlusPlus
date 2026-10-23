package org.rnair.driveplusplus.notificationpreferenceservice.infrastructure.entities

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import com.rnair.drivingplusplus.commons.notification.NotificationEventType
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "notification_preferences")
@IdClass(NotificationPreferenceKey::class)
class NotificationPreferenceJpaEntity : PanacheEntityBase {
    @Id
    @Column(name = "user_id")
    lateinit var userId: UUID

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    lateinit var eventType: NotificationEventType

    @Id
    @Enumerated(EnumType.STRING)
    lateinit var channel: NotificationChannel

    var enabled: Boolean = true

    @Column(name = "quiet_hours_start")
    var quietHoursStart: LocalTime? = null

    @Column(name = "quiet_hours_end")
    var quietHoursEnd: LocalTime? = null
}

class NotificationPreferenceKey(
    var userId: UUID? = null,
    var eventType: NotificationEventType? = null,
    var channel: NotificationChannel? = null,
) : Serializable {
    constructor() : this(null, null, null)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotificationPreferenceKey) return false
        return userId == other.userId && eventType == other.eventType && channel == other.channel
    }

    override fun hashCode(): Int {
        var result = userId?.hashCode() ?: 0
        result = 31 * result + (eventType?.hashCode() ?: 0)
        result = 31 * result + (channel?.hashCode() ?: 0)
        return result
    }
}
