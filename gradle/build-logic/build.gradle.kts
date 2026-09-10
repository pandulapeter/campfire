/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.gradle)
    implementation(libs.kotlin)
}

gradlePlugin {
    plugins {
        register("library") {
            id = "campfire-library"
            implementationClass = "com.pandulapeter.campfire.buildLogic.plugins.LibraryPlugin"
        }
        register("compose-library") {
            id = "campfire-compose-library"
            implementationClass = "com.pandulapeter.campfire.buildLogic.plugins.ComposeLibraryPlugin"
        }
    }
}
