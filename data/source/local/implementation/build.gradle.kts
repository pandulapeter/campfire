@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

plugins {
    id("campfire-library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    // Room has no wasmJs target, so its implementation is shared by every other platform through the "room" group,
    // while the web build backs the same interfaces with the browser's localStorage.
    applyHierarchyTemplate {
        common {
            group("room") {
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
        getByName("roomMain").dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        commonMain.dependencies {
            api(project(":data:source:local:api"))
            implementation(libs.koin.core)
            implementation(libs.kotlin.coroutines)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlin.browser)
            implementation(libs.kotlin.serialization.json)
        }
    }
}

dependencies {
    // Room generates the database implementation separately for every target.
    add("kspAndroid", libs.androidx.room.codegen)
    add("kspDesktop", libs.androidx.room.codegen)
    add("kspIosArm64", libs.androidx.room.codegen)
    add("kspIosSimulatorArm64", libs.androidx.room.codegen)
}
