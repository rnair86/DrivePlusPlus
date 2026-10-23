# appointment-service

Instructor availability and one-to-one lessons. Listens on port 8082. Swagger UI is at <http://localhost:8082/q/swagger-ui/>.

HTTP paths:

- `/api/v1/availability`
- `/api/v1/appointments`, including `PUT /{id}` to reschedule, `DELETE /{id}` to cancel, and `POST /{id}/complete`

Booking publishes `appointment.created`. Rescheduling publishes `appointment.rescheduled`. Cancelling publishes `appointment.cancelled`. Completing a lesson does not publish an event.

Every 15 minutes the service looks for confirmed lessons that start within 24 hours and have not been reminded yet. Each one publishes `appointment.reminder.due`.
