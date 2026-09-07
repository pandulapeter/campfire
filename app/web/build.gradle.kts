@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    wasmJs {
        outputModuleName = "campfire"
        browser {
            commonWebpackConfig {
                outputFileName = "campfire.js"
            }
        }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":data:repository:implementation"))
            implementation(project(":data:source:local:implementation"))
            implementation(project(":data:source:remote:implementation"))
            implementation(project(":domain:implementation"))
            implementation(project(":presentation"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.core)
        }
    }
}
