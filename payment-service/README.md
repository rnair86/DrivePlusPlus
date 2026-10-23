# payment-service

Invoices and payments. Listens on port 8084. Swagger UI is at <http://localhost:8084/q/swagger-ui/>.

HTTP path: `/api/v1/invoices`, including `POST /{id}/payments` and `DELETE /{id}` to cancel.

`course.enrollment.created` creates one invoice due 14 days later and publishes `payment.invoice.created`. A redelivery of that message can create a second invoice. There is no deduplication.

Payments can be partial. The invoice becomes `PAID` only when the paid total equals the invoice amount. A further payment on a `PAID` or `CANCELLED` invoice is rejected, and a payment larger than the remaining balance is rejected. Each accepted payment publishes `payment.received`.

Cancel rejects an invoice that is already `PAID` or `CANCELLED`. `PENDING` and `OVERDUE` can be cancelled, and cancel does not publish an event.

Once an hour the service publishes `payment.reminder.due` for `PENDING` invoices due within 3 days that have not been reminded yet. It then marks invoices whose due date is before today as `OVERDUE` and publishes `payment.overdue`. An invoice due today is not overdue.
