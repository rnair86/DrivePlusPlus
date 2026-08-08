pluginManagement {
//    val quarkusPluginVersion: String by project
//    val quarkusPluginId: String by project
    includeBuild("..\\build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
//    plugins {
//        id(quarkusPluginId) version quarkusPluginVersion
//    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
    versionCatalogs {
        create("libs") {
            from(files("..\\gradle\\libs.versions.toml"))
        }
    }
}
rootProject.name = "user-service"
include(":dpp-commons")
project(":dpp-commons").projectDir = file("..\\dpp-commons")
