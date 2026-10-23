package org.rnair.driveplusplus.notificationservice.infrastructure.sender

import com.rnair.drivingplusplus.commons.notification.NotificationChannel
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.notificationservice.core.models.SendResult
import org.rnair.driveplusplus.notificationservice.core.repository.NotificationSender
import java.util.UUID

@ApplicationScoped
class LoggingEmailSender {
    fun send(
        to: String,
        subject: String?,
        body: String,
    ): SendResult = SendResult(true, UUID.randomUUID().toString(), null)
}

@ApplicationScoped
class SmsSender {
    fun send(
        to: String,
        body: String,
    ): SendResult = SendResult(true, UUID.randomUUID().toString(), null)
}

@ApplicationScoped
class PushSender {
    fun send(
        to: String,
        body: String,
    ): SendResult = SendResult(true, UUID.randomUUID().toString(), null)
}

@ApplicationScoped
class CompositeNotificationSender(
    private val emailSender: LoggingEmailSender,
    private val smsSender: SmsSender,
    private val pushSender: PushSender,
) : NotificationSender {
    override fun send(
        channel: NotificationChannel,
        recipient: String,
        subject: String?,
        body: String,
    ): SendResult =
        when (channel) {
            NotificationChannel.EMAIL -> emailSender.send(recipient, subject, body)
            NotificationChannel.SMS -> smsSender.send(recipient, body)
            NotificationChannel.PUSH -> pushSender.send(recipient, body)
        }
}
