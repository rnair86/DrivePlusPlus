plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.quarkus.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.kotlin.noarg.plugin)
}