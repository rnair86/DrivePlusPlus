# Drive Plus Plus

Drive Plus Plus is a driving-school management platform. It covers the day-to-day work of the school: people, courses, lessons, tests, payments, and the notifications around them.

Each area is its own service. Services share nothing but a Postgres instance and a RabbitMQ broker, so the system can stay small on one machine and grow later without a rewrite.

## Features

- Register students and instructors, and sign in.
- Create courses, class batches, and sessions. Enroll a student in a batch.
- Book one-to-one lessons against an instructor's availability. Reschedule, cancel, and complete them.
- Schedule mock and official tests, reschedule them, and record a result.
- Raise an invoice when a student enrolls. Record partial payments, mark overdue invoices, and cancel unpaid ones.
- Keep each person's notification preferences, and send email when a relevant event happens.

Email delivery is a local stub. It records a successful send and does not call a mail provider.

## Tech stack

- Kotlin on Quarkus, using virtual threads and blocking JDBC rather than the reactive stack.
- PostgreSQL, one database per service. Flyway migrates each database on startup.
- RabbitMQ topic exchange `dpp.events` for anything that is not a direct read.
- Gradle, with a shared convention plugin in `build-logic`. Shared types live in `dpp-commons`.

Inside a service, `core` and `application` do not import the framework. REST, JPA, and messaging stay in `web` and `infrastructure`.

## How it works

A client calls one service over HTTP. That service writes its own database and, when something else needs to know, publishes an event. The routing key is the event type, for example `course.enrollment.created`. Consumers bind their own queues, so each service gets its own copy of a message.

Typical flow:

1. Registering a student publishes `user.student.registered`. The preference service seeds that student's email preferences. The notification service sends a welcome email.
2. Enrolling the student publishes `course.enrollment.created`. The payment service creates an invoice due in 14 days.
3. Booking a lesson, scheduling a test, or recording a payment publishes its own event. The notification service emails the people named on that event, unless their preference for it is off.
4. Appointment, test, and payment services each run an hourly job that publishes reminder and overdue events. Those go through the same notification path.

Changing a preference publishes `preference.updated`. The notification service keeps a local copy and does not ask the preference service at send time.

## Services

| Service | Port | Database | Responsibility |
|---|---|---|---|
| user-service | 8080 | identity | Students, instructors, login |
| course-service | 8081 | course | Courses, batches, sessions, enrollment |
| appointment-service | 8082 | appointment | Availability and lessons |
| test-service | 8083 | test | Test schedules and results |
| payment-service | 8084 | payment | Invoices and payments |
| notification-preference-service | 8085 | preferences | Per-user, per-event email preferences |
| notification-service | 8086 | notification | Templates, delivery log, outbound email |

HTTP APIs are under `/api/v1/`.

## Local setup

From the repo root, start Postgres and RabbitMQ:

```shell
docker compose -f local/docker-compose.infrastructure.yml up -d
```

Postgres listens on `localhost:5432`. RabbitMQ listens on `5672`, and its management UI is on `http://localhost:15672`. Database names, users, and the broker accounts are in `local/docker-compose.infrastructure.yml` and `local/rabbitmq/definitions.json`. Each service's `application.properties` points at its own database and broker user.

Run one service:

```shell
./gradlew :user-service:quarkusDev
```

Replace `user-service` with any module in the table. Flyway applies that service's migrations on startup.

Build and test everything:

```shell
./gradlew build
```

The tests use the local Postgres and RabbitMQ from the compose file, so those containers need to be up.

Run every service in Docker, on the same ports, against that Postgres and RabbitMQ:

```shell
./gradlew quarkusBuild -x test
docker compose -f local/docker-compose.services.yml up -d --build
```

`gradlew :user-service:quarkusDev` still uses `localhost`. The containers use the Postgres and RabbitMQ hostnames on the infrastructure network.
