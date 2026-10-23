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


# MANDATORY TASK PLANNING PROTOCOL

Before writing, modifying, or executing any code for a new task, you MUST halt execution and complete the planning phase. 

## 1. Create the Plan File
You must create a new markdown file in the `plans/` directory named `Plan-{Task_Name}.md`, where `{Task_Name}` is a hyphenated, concise summary of the objective.

## 2. Populate the Plan Structure
The file MUST contain the following exact sections:
* **Brief Description:** A concise summary of the requested changes and the overall objective.
* **Implementation Strategy:** A detailed technical explanation of how you will execute the task.
* **Impacted Services:** A comprehensive list of all modules, APIs, databases, or components affected by this change.
* **Architecture Justification:** An explanation of why this specific approach/pattern was chosen over alternatives.
* **Risk Assessment:** Potential edge cases, regressions, security concerns, or bottlenecks to watch out for.
* **Execution Checklist:** A granular, sequential list of subtasks formatted as markdown checkboxes (`- [ ]`).

## 3. Execution & Tracking
* **Strict Halt:** Do not proceed to coding until this document is fully generated.
* **Live Updates:** As you progress through the implementation, you MUST actively update the `Plan-{Task_Name}.md` file to check off (`- [x]`) each subtask.
* **Completion State:** You may only consider the task complete when all items on the Execution Checklist are verified and marked as done.