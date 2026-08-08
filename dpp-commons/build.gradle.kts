plugins {
    id("dpp.kotlin-library")
}

val quarkusPlatformGroupId: String = project.property("quarkusPlatformGroupId") as String
val quarkusPlatformArtifactId: String = project.property("quarkusPlatformArtifactId") as String
val quarkusPlatformVersion: String = project.property("quarkusPlatformVersion") as String

dependencies {
    api(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))
    api("io.quarkus:quarkus-messaging-rabbitmq")
    api("io.quarkus:quarkus-hibernate-orm-panache-kotlin")
    api("org.mindrot:jbcrypt:0.4")
}
