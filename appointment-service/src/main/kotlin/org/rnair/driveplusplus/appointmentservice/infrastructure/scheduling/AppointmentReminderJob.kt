package org.rnair.driveplusplus.appointmentservice.infrastructure.scheduling

import io.quarkus.scheduler.Scheduled
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.appointmentservice.application.usecase.FindDueReminderAppointmentsUseCase

@ApplicationScoped
class AppointmentReminderJob(
    private val findDueReminderAppointmentsUseCase: FindDueReminderAppointmentsUseCase,
) {
    @Scheduled(every = "15m")
    @RunOnVirtualThread
    fun run() = findDueReminderAppointmentsUseCase.execute()
}
