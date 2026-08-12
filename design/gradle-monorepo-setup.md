# Setting Up the Monorepo — Gradle + Kotlin DSL

This sets up one Gradle build containing `ds-commons` plus all 7 services, with a shared **convention plugin** so you don't repeat the same 20 lines of Quarkus config in 7 places, and a **version catalog** so every module is guaranteed to be on the same Kotlin/Quarkus versions.

## 1. Final directory layout

```
driving-school-platform/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build-logic/                          # composite build, holds convention plugins
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── ds.kotlin-library.gradle.kts
│       └── ds.quarkus-service.gradle.kts
├── ds-commons/
│   ├── build.gradle.kts
│   └── src/main/kotlin/...
├── identity-service/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/drivingschool/identity/
│       │   ├── domain/{model,repository,event,exception}/
│       │   ├── application/usecase/
│       │   ├── infrastructure/{persistence,messaging}/
│       │   └── presentation/rest/{resource,dto}/
│       ├── main/resources/application.yml
│       └── test/kotlin/com/drivingschool/identity/
│           ├── domain/                    # plain JUnit5, no Quarkus
│           ├── application/                # plain JUnit5, fake repositories/publisher
│           └── infrastructure/             # @QuarkusTest + Testcontainers
├── course-service/                        # same internal shape as identity-service
├── appointment-service/                   # same internal shape
├── test-service/                          # same internal shape
├── payment-service/                       # same internal shape
├── notification-preference-service/       # same internal shape
├── notification-service/                  # same internal shape
└── charts/                               # Helm charts from the system design doc
```

Every service module has the **same four-package skeleton** (`domain`, `application`, `infrastructure`, `presentation`) described in the system design doc. Gradle itself doesn't need to know these packages exist — they're just Kotlin package structure inside one `src/main/kotlin` source set — but we do want the build to *enforce* that the dependency rule between them is actually respected. That's what §12 below adds.

Each service module is a normal Quarkus app (Quarkus auto-generates `src/main/docker/Dockerfile.jvm` etc. the first time you build).

---

## 2. Version catalog — `gradle/libs.versions.toml`

Single source of truth for versions. Both the root build and `build-logic` point at this same file.

```toml
[versions]
kotlin = "2.0.20"
quarkus = "3.15.3"
jackson = "2.17.2"
restAssured = "5.5.0"
archunit = "1.3.0"

[libraries]
quarkus-gradle-plugin = { module = "io.quarkus:io.quarkus.gradle.plugin", version.ref = "quarkus" }
kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
kotlin-allopen-plugin = { module = "org.jetbrains.kotlin:kotlin-allopen", version.ref = "kotlin" }
kotlin-noarg-plugin = { module = "org.jetbrains.kotlin:kotlin-noarg", version.ref = "kotlin" }
jackson-module-kotlin = { module = "com.fasterxml.jackson.module:jackson-module-kotlin", version.ref = "jackson" }
rest-assured = { module = "io.rest-assured:rest-assured", version.ref = "restAssured" }
archunit-junit5 = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
quarkus = { id = "io.quarkus", version.ref = "quarkus" }
```

---

## 3. Root `settings.gradle.kts`

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "driving-school-platform"

include(
    "ds-commons",
    "identity-service",
    "course-service",
    "appointment-service",
    "test-service",
    "payment-service",
    "notification-preference-service",
    "notification-service"
)
```

`includeBuild("build-logic")` is what makes the convention plugins defined there usable as ordinary plugin IDs (`id("ds.quarkus-service")`) everywhere else in the repo.

---

## 4. Root `gradle.properties`

```properties
quarkusPlatformGroupId=io.quarkus.platform
quarkusPlatformArtifactId=quarkus-bom
quarkusPlatformVersion=3.15.3

org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
kotlin.code.style=official
```

Root `gradle.properties` values are visible as project properties in every subproject, so `quarkusPlatformVersion` etc. can be read from the convention plugin without repeating it.

---

## 5. Root `build.gradle.kts`

Deliberately close to empty — all real configuration lives in the convention plugins. Optionally add repo-wide formatting:

```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1" apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
```

---

## 6. `build-logic` — the composite build for convention plugins

### `build-logic/settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))   // reuse the same catalog
        }
    }
}

rootProject.name = "build-logic"
```

### `build-logic/build.gradle.kts`

```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.quarkus.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.kotlin.noarg.plugin)
}
```

`kotlin-dsl` lets you write precompiled script plugins (the `.gradle.kts` files below) that become real plugin IDs elsewhere in the repo — the file name (minus `.gradle.kts`) is the plugin ID.

### `build-logic/src/main/kotlin/ds.kotlin-library.gradle.kts`

Applied to plain Kotlin modules (just `ds-commons` for now).

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}
```

### `build-logic/src/main/kotlin/ds.quarkus-service.gradle.kts`

Applied to every one of the 7 microservices — this is where virtual threads, Panache, RabbitMQ messaging, OIDC, etc. get wired up once.

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("io.quarkus")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.allopen")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))

    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-hibernate-orm-panache-kotlin")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")
    implementation("io.quarkus:quarkus-messaging-rabbitmq")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-opentelemetry")
    implementation("io.quarkus:quarkus-oidc")
    implementation("io.quarkus:quarkus-container-image-jib")   // for building images Helm will deploy

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation(libs.rest.assured)
    testImplementation(libs.archunit.junit5)   // enforces the domain/application/infrastructure/presentation dependency rule
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        javaParameters.set(true)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
```

Note: `allopen` is required because Quarkus/Hibernate need JPA entities and JAX-RS resources to be non-final classes to proxy them — Kotlin classes are final by default, so this plugin auto-opens anything annotated `@Entity`, `@Path`, etc. instead of you writing `open class` everywhere.

---

## 7. `ds-commons/build.gradle.kts`

```kotlin
plugins {
    id("ds.kotlin-library")
}

dependencies {
    implementation(libs.jackson.module.kotlin)
}
```

Put `EventEnvelope`, `ProblemDetail`, `RabbitTopology`, the shared enums, etc. under `ds-commons/src/main/kotlin/com/drivingschool/commons/`.

---

## 8. A service module's `build.gradle.kts`

This is genuinely all it takes, e.g. `identity-service/build.gradle.kts`:

```kotlin
plugins {
    id("ds.quarkus-service")
}

dependencies {
    implementation(project(":ds-commons"))
}
```

Same file, same shape, for `course-service`, `appointment-service`, `test-service`, `payment-service`, `notification-preference-service`, `notification-service` — just swap the module name. If one service needs something extra (e.g. notification-service needs a Twilio/SES SDK), add it as one more `implementation(...)` line in that module only; the shared config stays in `build-logic`.

---

## 9. Generate the wrapper

From the repo root:

```bash
gradle wrapper --gradle-version 8.10
```

Commit `gradlew`, `gradlew.bat`, and `gradle/wrapper/` so nobody needs a local Gradle install.

---

## 10. Everyday commands

```bash
# Run one service in dev mode (live reload)
./gradlew :identity-service:quarkusDev

# Build everything
./gradlew build

# Test one module
./gradlew :course-service:test

# Build a container image for one service (uses quarkus-container-image-jib)
./gradlew :appointment-service:build -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.registry=ghcr.io \
  -Dquarkus.container-image.group=your-org \
  -Dquarkus.container-image.tag=$(git rev-parse --short HEAD)

# Build + push images for every service (handy in CI)
./gradlew build -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true

# Native build for a specific service (optional, smaller/faster cold-start pods)
./gradlew :identity-service:build -Dquarkus.package.type=native
```

Each service's produced image name/tag is exactly what your Helm `values.yaml` `image.repository`/`image.tag` should point at (from the system design doc's chart layout).

---

## 11. Enforcing the Clean Architecture dependency rule with ArchUnit

Nothing stops a developer from accidentally importing `jakarta.persistence.Entity` inside `domain.model`, or having `application` reach into `infrastructure`, unless something checks for it. Rather than relying on code review, add one ArchUnit test per service (or, better, one shared test class reused by every service) that fails the build if the dependency rule is violated.

Put this in `ds-commons` as a small test-fixtures artifact every service depends on for tests:

```kotlin
// ds-commons/src/testFixtures/kotlin/CleanArchitectureRules.kt
class CleanArchitectureRules(private val basePackage: String) {
    private val classes = ClassFileImporter().importPackages(basePackage)

    fun domainHasNoFrameworkDependencies() {
        noClasses().that().resideInAPackage("$basePackage.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta..", "org.hibernate..", "io.quarkus..", "$basePackage.infrastructure..", "$basePackage.presentation.."
            )
            .check(classes)
    }

    fun applicationOnlyDependsOnDomain() {
        noClasses().that().resideInAPackage("$basePackage.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.persistence..", "io.quarkus..", "$basePackage.infrastructure..", "$basePackage.presentation.."
            )
            .check(classes)
    }

    fun layersRespectDependencyDirection() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("$basePackage.domain..")
            .layer("Application").definedBy("$basePackage.application..")
            .layer("Infrastructure").definedBy("$basePackage.infrastructure..")
            .layer("Presentation").definedBy("$basePackage.presentation..")
            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Presentation")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation", "Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            .check(classes)
    }
}
```

Then each service just runs it against its own package in one line:

```kotlin
// identity-service/src/test/kotlin/.../ArchitectureTest.kt
class ArchitectureTest {
    private val rules = CleanArchitectureRules("com.drivingschool.identity")

    @Test fun `domain has no framework dependencies`() = rules.domainHasNoFrameworkDependencies()
    @Test fun `application only depends on domain`() = rules.applicationOnlyDependsOnDomain()
    @Test fun `layers respect dependency direction`() = rules.layersRespectDependencyDirection()
}
```

To share `CleanArchitectureRules` across service modules, add the `java-test-fixtures` plugin to `ds.kotlin-library.gradle.kts` and have each service depend on `testImplementation(testFixtures(project(":ds-commons")))`. This turns "don't let infrastructure leak into domain" from a code-review convention into something `./gradlew build` physically cannot pass if violated.

---

## 12. Splitting fast unit tests from slow integration tests

Because `domain`/`application` have zero Quarkus dependencies, their tests don't need `@QuarkusTest` at all — they're plain JUnit5 with fakes, and run in milliseconds. `infrastructure` tests do need a real Postgres/RabbitMQ via Quarkus Dev Services + Testcontainers, and are slower. Keeping both in the same `test` source set works, but tagging them lets you run the fast ones on every save and reserve the slow ones for CI/pre-merge:

```kotlin
// build-logic/src/main/kotlin/ds.quarkus-service.gradle.kts (addition)
tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
}

tasks.named("check") {
    dependsOn(tasks.named("integrationTest"))
}
```

Tag accordingly in each service:

```kotlin
@Tag("integration")
@QuarkusTest
class PanacheStudentRepositoryTest { /* real Postgres via Dev Services */ }

class RegisterStudentUseCaseTest { /* no tag, no Quarkus — plain JUnit5, runs in the default `test` task */ }
```

Now `./gradlew test` (fast, no containers) is what you run in a tight edit loop, and `./gradlew check` (which pulls in `integrationTest`) is what CI runs before merging — matching the unit/integration split called out in the system design doc's testing note.

---

## 13. Why this shape

- **One `settings.gradle.kts` include list** is the only place you register a new service — add the line, add the folder with a 4-line `build.gradle.kts`, done.
- **`build-logic` as a composite build** (rather than the older `buildSrc`) means editing a convention plugin doesn't invalidate the whole build's configuration cache the way `buildSrc` changes historically did, and it's the pattern Gradle currently recommends for shared build logic.
- **Version catalog shared between the root build and `build-logic`** means bumping the Quarkus or Kotlin version is a one-line change in `libs.versions.toml`, and every one of the 7 services picks it up identically — no drift between services that were touched at different times.
- **Virtual threads config lives in one place** (`ds.quarkus-service.gradle.kts` sets `sourceCompatibility = 21`; you annotate individual resource/consumer methods `@RunOnVirtualThread` in each service's own code, since that's a per-endpoint choice, not a build-level one).
- **The Clean Architecture dependency rule is enforced by the build, not just by convention** — the shared ArchUnit rules in `ds-commons`'s test fixtures mean a PR that lets `infrastructure` leak into `domain` fails `./gradlew check` in every one of the 7 services identically, without anyone having to remember to check for it in review.
