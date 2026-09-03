@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("gradle")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Campfire"
include(
    ":app:android",
    ":app:desktop",
    ":app:ios",
    ":data:model",
    ":data:repository:api",
    ":data:repository:implementation",
    ":data:source:local:api",
    ":data:source:local:implementation",
    ":data:source:remote:api",
    ":data:source:remote:implementation",
    ":domain:api",
    ":domain:implementation",
    ":presentation:android",
    ":presentation:android-debug-menu",
    ":presentation:desktop",
    ":presentation:ios",
    ":presentation:shared"
)
