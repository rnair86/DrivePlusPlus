package org.rnair.driveplusplus.paymentservice.infrastructure.scheduling

import io.quarkus.scheduler.Scheduled
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import org.rnair.driveplusplus.paymentservice.application.usecase.FindDueReminderInvoicesUseCase
import org.rnair.driveplusplus.paymentservice.application.usecase.MarkOverdueInvoicesUseCase

@ApplicationScoped
class PaymentReminderJob(
    private val findDueReminderInvoicesUseCase: FindDueReminderInvoicesUseCase,
    private val markOverdueInvoicesUseCase: MarkOverdueInvoicesUseCase,
) {
    @Scheduled(every = "1h")
    @RunOnVirtualThread
    fun run() {
        findDueReminderInvoicesUseCase.execute()
        markOverdueInvoicesUseCase.execute()
    }
}
