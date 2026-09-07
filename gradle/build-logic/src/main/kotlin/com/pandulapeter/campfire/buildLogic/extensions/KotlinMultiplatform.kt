package com.pandulapeter.campfire.buildLogic.extensions

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Configures the shared set of targets every Campfire library module compiles for: Android, desktop (JVM), iOS and web (wasmJs).
 * The Android namespace is derived from the Gradle path, so modules only need to override it when they want something else.
 */
@OptIn(ExperimentalWasmDsl::class)
internal fun Project.configureKotlinMultiplatform(
    extension: KotlinMultiplatformExtension
) = extension.apply {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.version("jvmTarget").toInt()))
    }
    extension.configure<KotlinMultiplatformAndroidLibraryTarget> {
        namespace = "com.pandulapeter.campfire" + path.replace(":", ".").replace("-", "_")
        minSdk = libs.version("android-minSdk").toInt()
        compileSdk = libs.version("android-compileSdk").toInt()
        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
    }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
    }
}
