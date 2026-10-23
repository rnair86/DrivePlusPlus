# notification-preference-service

Per-user notification preferences. Listens on port 8085. Swagger UI is at <http://localhost:8085/q/swagger-ui/>.

HTTP path: `/api/v1/users/{userId}/preferences`. `GET` lists the rows. `PUT` upserts a list. `PUT /{eventType}/{channel}` toggles `enabled` and keeps the quiet hours already stored. The user id in the path is the one that is saved.

`user.student.registered` and `user.instructor.registered` seed `EMAIL` preferences. A student gets appointment created, appointment reminder, test scheduled, test reminder, test result, payment due, and payment overdue. Any other role, including instructor, gets appointment created and class session scheduled. Seeding does not publish an event. A redelivery upserts the same rows.

Changing a preference publishes `preference.updated`. notification-service stores that update locally and does not call this service when it sends mail.
