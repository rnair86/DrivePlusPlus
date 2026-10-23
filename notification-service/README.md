# notification-service

Email notifications, templates, and the delivery log. Listens on port 8086. Swagger UI is at <http://localhost:8086/q/swagger-ui/>.

Calls to `/api/v1` need `Authorization: Bearer` from `POST /api/v1/auth/login` on user-service. Login itself is public. In Swagger UI, open **Authorize** and paste the raw token.

HTTP paths:

- `GET /api/v1/notifications`, with optional `userId`, `eventType`, `from`, and `to`
- `GET` and `PUT /api/v1/templates`

The service sends `EMAIL` only. The sender is a stub: it records a successful send and does not call a mail provider. The address is the `email` on the payload when that field is present and not empty. Otherwise the address is the user id.

Startup inserts one `EMAIL` template per event this service sends. The body uses `{{recipient}}`. A missing template writes nothing and publishes nothing.

A send is skipped when the same source event, user, and channel are already in the log. A disabled preference is stored as `SUPPRESSED_BY_PREFERENCE` and publishes nothing. A successful send publishes `notification.sent`. A failed send publishes `notification.failed`.

Events that produce mail: `user.student.registered`, `user.instructor.registered`, `appointment.created`, `appointment.reminder.due`, `appointment.cancelled`, `test.scheduled`, `test.reminder.due`, `test.result.published`, `payment.reminder.due`, `payment.overdue`, and `payment.received`. Other events on the queues are ignored. `preference.updated` only updates the local preference cache.
