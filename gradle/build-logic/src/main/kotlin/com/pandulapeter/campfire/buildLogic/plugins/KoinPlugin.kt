/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.buildLogic.plugins

import com.pandulapeter.campfire.buildLogic.extensions.libs
import com.pandulapeter.campfire.buildLogic.extensions.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.koin.compiler.plugin.KoinGradleExtension

/**
 * The Koin compiler plugin and the two libraries its generated code calls, for every module that declares a definition,
 * so that the plugin is applied and configured in one place. It does not choose the module's convention plugin, and
 * waits for Kotlin Multiplatform rather than assuming `campfire-library` came first in the `plugins {}` block.
 */
class KoinPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        pluginManager.withPlugin(libs.pluginId("kotlin-multiplatform")) {
            pluginManager.apply(libs.pluginId("koin-compiler"))
            // A failed graph check is reported as the missing definition; the line advertising an AI service after it is
            // left out. Only :app:di runs the whole graph check, but one setting everywhere keeps the modules alike.
            extensions.configure<KoinGradleExtension> {
                aiAssist.set(false)
            }
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.findLibrary("koin-annotations").get())
                    implementation(libs.findLibrary("koin-core").get())
                }
            }
        }
    }
}
