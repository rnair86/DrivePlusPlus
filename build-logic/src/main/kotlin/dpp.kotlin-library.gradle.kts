import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.`java-library`
import org.gradle.kotlin.dsl.repositories
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

repositories {
    mavenCentral()
}

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(25)
}

dependencies {
    add("testImplementation", "org.jetbrains.kotlin:kotlin-test")
}