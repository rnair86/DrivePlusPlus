plugins {
    `kotlin-dsl`
    alias(libs.plugins.spotless)
}

dependencies {
    implementation(libs.quarkus.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.kotlin.noarg.plugin)
}

spotless {
    kotlinGradle {
        target("src/main/kotlin/**/*.gradle.kts")
        ktlint()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}