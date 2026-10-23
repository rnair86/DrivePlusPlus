# test-service

Mock and official test schedules. Listens on port 8083. Swagger UI is at <http://localhost:8083/q/swagger-ui/>.

HTTP path: `/api/v1/tests`, including `PUT /{id}` to reschedule, `DELETE /{id}` to cancel, and `POST /{id}/result` to record a result.

Scheduling publishes `test.scheduled`. Rescheduling publishes `test.rescheduled`. Cancelling publishes `test.cancelled`. Recording a result publishes `test.result.published`.

A completed test cannot be rescheduled, cancelled, or given another result. A cancelled test cannot be cancelled again or given a result. Rescheduling a cancelled test is allowed and sets the status back to `RESCHEDULED`.

Every 15 minutes the service looks for tests in `SCHEDULED` that start within 24 hours and have not been reminded yet. Each one publishes `test.reminder.due`.
