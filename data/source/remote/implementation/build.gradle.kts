@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

plugins {
    id("campfire-library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
}

kotlin {
    // Retrosheet makes the Kotlin/Wasm compiler emit a binary browsers refuse to load, so it is shared by every other
    // platform through the "retrosheet" group while the web build queries the same Google Sheets CSV endpoint itself.
    applyHierarchyTemplate {
        common {
            group("retrosheet") {
                // The Android target of the AGP multiplatform library plugin isn't matched by withAndroidTarget().
                withCompilations { it.target.name == "android" }
                withJvm()
                group("ios") {
                    withIos()
                }
            }
            withWasmJs()
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":data:source:remote:api"))
            implementation(libs.koin.core)
            implementation(libs.kotlin.coroutines)
            implementation(libs.kotlin.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            // The Ktorfit plugin only generates the service implementation for commonMain, so SongService and the
            // Retrosheet annotation it carries have to live there even though only the "retrosheet" group calls it.
            implementation(libs.ktorfit)
            implementation(libs.theapache64.retrosheet)
        }
        wasmJsMain.dependencies {
            // The CSV format Retrosheet decodes sheet responses with, used directly here.
            implementation(libs.softwork.csv)
            // Unlike the other targets, the browser one has no engine bundled with ktor-client-core.
            implementation(libs.ktor.client.js)
        }
    }
}
