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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Starts the release image once with `-XX:ArchiveClassesAtExit` and adds the archive it writes to the launcher's
 * configuration. The process is started with `ProcessBuilder` rather than `ExecOperations`, which has no timeout, and
 * its output goes to a file, so that a chatty run never blocks on a full pipe.
 */
abstract class RecordClassDataArchive : DefaultTask() {

    @get:Internal
    abstract val appImage: DirectoryProperty

    @get:Input
    abstract val targetsWindows: Property<Boolean>

    @TaskAction
    fun record() {
        val image = appImage.get().asFile
        val launcher = if (targetsWindows.get()) image.resolve("Campfire.exe") else image.resolve("bin/Campfire")
        val appDirectory = if (targetsWindows.get()) image.resolve("app") else image.resolve("lib/app")
        val configuration = appDirectory.resolve("Campfire.cfg")
        val archive = appDirectory.resolve("campfire.jsa")
        if (!launcher.isFile || !configuration.isFile) throw GradleException("There is no release app image in $image to train.")
        // JBR 21's AWT speaks X11 alone, so without a display the run would only wait for its timeout.
        if (!targetsWindows.get() && System.getenv("DISPLAY").isNullOrEmpty()) {
            throw GradleException("Recording the class data sharing archive starts the app, which needs a display: run the packaging under xvfb-run.")
        }
        // The image may be up to date from an earlier run, and a JVM given a dynamic archive cannot record another on
        // top of it. A run that failed after the dump leaves the archive read-only, which on Windows refuses the delete.
        val lines = configuration.readLines().filterNot { it.startsWith(ARCHIVE_OPTION) }
        configuration.writeText(lines.joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
        if (archive.exists()) {
            archive.setWritable(true)
            if (!archive.delete()) throw GradleException("Could not delete the previous $archive.")
        }
        val home = temporaryDir.resolve("home")
        home.deleteRecursively()
        home.mkdirs()
        val output = temporaryDir.resolve("training.log")
        try {
            val process = ProcessBuilder(launcher.absolutePath).apply {
                val environment = environment()
                // Quoted, which HotSpot's reading of the variable honours, so a checkout under a folder with a space in
                // its name still trains.
                environment["JAVA_TOOL_OPTIONS"] =
                    "-XX:ArchiveClassesAtExit=\"${archive.absolutePath}\" -Dcampfire.trainingRun=true -Duser.home=\"${home.absolutePath}\""
                if (targetsWindows.get()) {
                    // The data directory comes from %APPDATA% on Windows, not from user.home.
                    environment["APPDATA"] = home.resolve("AppData/Roaming").absolutePath
                    environment["LOCALAPPDATA"] = home.resolve("AppData/Local").absolutePath
                } else {
                    environment.remove("XDG_DATA_HOME")
                }
                redirectErrorStream(true)
                redirectOutput(output)
            }.start()
            if (!process.waitFor(3, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                throw GradleException("The training run did not end in three minutes:\n${output.readTextOrEmpty()}")
            }
            if (process.exitValue() != 0) {
                throw GradleException("The training run exited with ${process.exitValue()}:\n${output.readTextOrEmpty()}")
            }
            // The dump also prints a few dozen warnings about JFR and proxy classes it skips, which are expected.
            if (!archive.isFile || archive.length() < 10L * 1024 * 1024) {
                throw GradleException("The training run wrote no class data sharing archive:\n${output.readTextOrEmpty()}")
            }
        } finally {
            home.deleteRecursively()
        }
        // The JVM writes it read-only, which on Windows stops the next jlink or jpackage run from clearing the image.
        archive.setWritable(true)
        val section = lines.indexOf("[JavaOptions]")
        if (section < 0) throw GradleException("There is no [JavaOptions] section in $configuration to add the archive to.")
        val option = "$ARCHIVE_OPTION\$APPDIR/${archive.name}"
        configuration.writeText((lines.take(section + 1) + option + lines.drop(section + 1)).joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
        logger.lifecycle("Recorded ${archive.length() / (1024 * 1024)} MB of class data sharing archive into $archive")
    }

    private fun File.readTextOrEmpty() = takeIf { it.isFile }?.readText().orEmpty()

    private companion object {
        const val ARCHIVE_OPTION = "java-options=-XX:SharedArchiveFile="
    }
}
