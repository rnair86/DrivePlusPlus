plugins {
    id("dpp.quarkus-service")
}
dependencies {
    implementation(project(":dpp-commons"))
    testImplementation("io.mockk:mockk:1.14.11")
}
