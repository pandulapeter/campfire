plugins {
    id("campfire-library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:source:local:api"))
            implementation(project(":chordpro"))
            implementation(libs.koin.core)
            implementation(libs.kotlin.coroutines)
            implementation(libs.kotlin.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        wasmJsMain.dependencies {
            // The Origin Private File System is reached through the browser APIs, see FileStorage.wasmJs.kt.
            implementation(libs.kotlin.browser)
        }
    }
}
