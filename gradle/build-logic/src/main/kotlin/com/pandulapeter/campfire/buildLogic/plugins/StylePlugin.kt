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

import com.diffplug.gradle.spotless.SpotlessExtension
import com.pandulapeter.campfire.buildLogic.extensions.libs
import com.pandulapeter.campfire.buildLogic.extensions.pluginId
import com.pandulapeter.campfire.buildLogic.extensions.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * ktlint through Spotless (`spotlessCheck`, `spotlessApply`), with the rules the root `.editorconfig` leaves enabled.
 * Applied by each module on its own rather than from a root `subprojects {}` block, which would be cross-project
 * configuration. The Kotlin sources are found by path, so every source set of every kind of module is covered without
 * asking a Kotlin or Android plugin for its source sets.
 *
 * Every Kotlin compilation also treats its warnings as errors. A warning is only printed by the compilation that runs,
 * and with most of them restored from the build cache, one left behind (an unnecessary safe call, an unused variable,
 * a deprecated call) scrolls past once and is never seen again. Matched by task type, so the multiplatform libraries,
 * the JVM and Android apps, and every target's main and test compilations are covered alike, whichever Kotlin plugin
 * a module applies and in whatever order.
 */
class StylePlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs.pluginId("spotless"))
        val ktlintVersion = libs.version("ktlint")
        extensions.configure<SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                ktlint(ktlintVersion)
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint(ktlintVersion)
            }
        }
        tasks.withType<KotlinCompilationTask<*>>().configureEach {
            compilerOptions.allWarningsAsErrors.set(true)
        }
    }
}
