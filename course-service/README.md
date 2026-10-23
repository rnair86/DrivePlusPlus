# course-service

Courses, class batches, sessions, and enrollment. Listens on port 8081. Swagger UI is at <http://localhost:8081/q/swagger-ui/>.

HTTP paths:

- `/api/v1/courses`
- `/api/v1/batches` and `/api/v1/batches/{id}/sessions`
- `/api/v1/enrollments`

Creating a batch publishes `class.batch.created`. Scheduling, rescheduling, or cancelling a session publishes `class.session.scheduled`, `class.session.rescheduled`, or `class.session.cancelled`. Enrolling a student publishes `course.enrollment.created`. Withdrawing that enrollment publishes `course.enrollment.withdrawn`.

`course.enrollment.created` is what payment-service uses to raise an invoice. The payload includes the student id, enrollment id, and course price.
