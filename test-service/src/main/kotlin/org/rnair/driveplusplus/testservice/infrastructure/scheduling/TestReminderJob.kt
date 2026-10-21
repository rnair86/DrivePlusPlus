package org.rnair.driveplusplus.testservice.infrastructure.scheduling

import io.quarkus.scheduler.Scheduled
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.testservice.application.usecase.FindDueReminderTestsUseCase

@ApplicationScoped
class TestReminderJob(
    private val findDueReminderTestsUseCase: FindDueReminderTestsUseCase,
) {
    @Scheduled(every = "15m")
    @RunOnVirtualThread
    fun run() = findDueReminderTestsUseCase.execute()
}
