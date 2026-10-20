# Driving School Management Platform 

## Buisness Requirements
This project is a Driving School management platform called Drive Plus Plus and 
it is intended to be used to manage day to day activities of driving school like

- register New students and teachers
- Manage student classes
- Create and manage courses
- create and manage appointments
- create and manage Test scedules
- send out notifications to students and teachers about upcoming classes / tests/ payment reminders
- Create and manage notification preferences for each type of notification event
- Microservice architecture, cheap to run on day one, scalable later without re-architecting.
- Clean Architecture inside each service — core/application have zero framework imports and are unit-testable without framework dependencies.

## Initial High Level System Design
The Document with the Initial System design overview is available at @../driving-school-system-design.md  please review before proceeding 

## Technical Decisions 
- Microservice architecture, cheap to run on day one, scalable later without re-architecting.
- Kotlin on Quarkus, using virtual threads (@RunOnVirtualThread) instead of the reactive stack : plain blocking JDBC/Hibernate, high concurrency, low memory overhead.
- Clean Architecture inside each service 
- RabbitMQ for all cross-service communication that isn't a direct synchronous read.
- PostgreSQL, for Database
- Flyway for migration
- Helm charts, one per service.
= Gradle for dependency management with shared plugin


## Coding standards

1. Use latest versions of libraries and idiomatic approaches as of today
2. Keep it simple - NEVER over-engineer, ALWAYS simplify, NO unnecessary defensive programming. No extra features - focus on simplicity.
3. Be concise. Keep README minimal. IMPORTANT: no emojis ever
4. When hitting issues, always identify root cause before trying a fix. Do not guess. Prove with evidence, then fix the root cause.


## Starting Point 
- The Sharable gradle plugins are already created and  included in the `build-logic` folder 
- `dpp-commons` contains common  code shared by all the services 
- `local` folder contains docker-compose to set up infrastructure to test locally
- For the services there have been some deviation to the project strcture and naming conventions from those mentioned in the design document, so please update the system design document to reflect the changes 


## Working Documentation
For each service Create a `PLAN.md`  and document the steps that is required to complete the service as a check list 
and while working on each of the steps further enrich the document with a brief summary of what was done, any issues encountered and how it was fixed
once a step is completed and verified to be completed, check it off on the checklist before moving on the the next task  

PLEASE REVIEW PLAN.md before proceeding 