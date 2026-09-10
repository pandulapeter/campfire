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
    // Not FAIL_ON_PROJECT_REPOS, because the Kotlin/Wasm browser tooling insists on adding the Node.js and Yarn
    // download repositories to the root project. PREFER_SETTINGS ignores those in favour of the ones declared below.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // The distributions the Kotlin/Wasm browser tooling downloads through Gradle.
        exclusiveContent {
            forRepository {
                ivy("https://nodejs.org/dist") {
                    name = "Node.js distributions"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("org.nodejs") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/yarnpkg/yarn/releases/download") {
                    name = "Yarn distributions"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("com.yarnpkg") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/WebAssembly/binaryen/releases/download") {
                    name = "Binaryen distributions"
                    patternLayout { artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("com.github.webassembly") }
        }
    }
}

/**
 * `local.properties` is never committed, so it is where real signing credentials and API keys belong. Loading it
 * here, and letting it overwrite the matching Gradle property on every project, means the build files only ever read
 * an ordinary property and never learn that an override mechanism exists.
 *
 * Anything not overridden falls back to the default declared in `gradle.properties`, which is what lets a fresh
 * clone build every variant without being handed a single secret.
 */
val localProperties = java.util.Properties()
java.io.File(settingsDir, "local.properties").let { file ->
    if (file.exists()) file.inputStream().use(localProperties::load)
}
gradle.beforeProject {
    localProperties.forEach { name, value -> extensions.extraProperties.set(name.toString(), value) }
}

rootProject.name = "Campfire"
include(
    ":app:android",
    ":app:desktop",
    ":app:ios",
    ":app:web",
    ":chordpro",
    ":data:model",
    ":data:repository:api",
    ":data:repository:implementation",
    ":data:source:local:api",
    ":data:source:local:implementation",
    ":data:source:remote:api",
    ":data:source:remote:implementation",
    ":domain:api",
    ":domain:implementation",
    ":presentation"
)
