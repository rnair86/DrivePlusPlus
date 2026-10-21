plugins {
    id("dpp.quarkus-service")
}

dependencies {
    implementation(project(":dpp-commons"))
    implementation("io.quarkus:quarkus-scheduler")
    testImplementation("io.mockk:mockk:1.14.11")
}
