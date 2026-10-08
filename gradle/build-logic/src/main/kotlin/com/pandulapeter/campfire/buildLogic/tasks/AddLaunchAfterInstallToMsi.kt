/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.buildLogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/** Runs `add-launch-after-install.ps1` over each .msi, which fails the build rather than leave an installer without it. */
abstract class AddLaunchAfterInstallToMsi : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Internal
    abstract val packages: ConfigurableFileCollection

    @get:InputFile
    abstract val script: RegularFileProperty

    @get:Input
    abstract val launcherName: Property<String>

    @get:Input
    abstract val checkboxText: Property<String>

    @TaskAction
    fun addLaunchAfterInstall() {
        val msis = packages.files.filter { it.isFile }
        if (msis.isEmpty()) throw GradleException("There is no .msi to add the launch checkbox to.")
        msis.forEach { msi ->
            execOperations.exec {
                commandLine(
                    "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", script.get().asFile.absolutePath,
                    "-Path", msi.absolutePath,
                    "-Launcher", launcherName.get(),
                    "-Text", checkboxText.get(),
                )
            }
        }
    }
}
