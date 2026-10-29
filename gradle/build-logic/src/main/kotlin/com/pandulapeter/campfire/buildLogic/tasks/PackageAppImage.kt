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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Packs the release app image into an AppImage, the one file that starts on any Linux distribution without being
 * installed. The image goes into an AppDir as it is, beside the three files an AppDir has to have at its root: the
 * `AppRun` the AppImage runtime starts, the desktop entry and the icon it names. appimagetool then puts the AppDir into
 * a SquashFS image behind the runtime. The runtime is a static executable of its own, so the file needs no FUSE library
 * on the machine it runs on, and the app image is the one the `.deb` holds, its class data sharing archive included.
 */
abstract class PackageAppImage : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputDirectory
    abstract val appImage: DirectoryProperty

    @get:InputFile
    abstract val icon: RegularFileProperty

    @get:Input
    abstract val applicationName: Property<String>

    @get:Input
    abstract val comment: Property<String>

    @get:Input
    abstract val categories: Property<String>

    @get:Input
    abstract val windowClassName: Property<String>

    @get:Input
    abstract val urlScheme: Property<String>

    /** The appimagetool to pack with; empty means the one on the `PATH`. */
    @get:Input
    abstract val appImageTool: Property<String>

    /** The runtime the AppImage starts with; empty means the one appimagetool downloads for itself. */
    @get:Input
    abstract val runtimeFile: Property<String>

    @get:Input
    abstract val architecture: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun packageAppImage() {
        val appDirectory = temporaryDir.resolve("Campfire.AppDir")
        appDirectory.deleteRecursively()
        appDirectory.mkdirs()
        // Not copyRecursively, which drops the executable bit of the launcher and of the runtime's own binaries.
        execOperations.exec { commandLine("cp", "-a", appImage.get().asFile.absolutePath, appDirectory.resolve(IMAGE_FOLDER).absolutePath) }
        appDirectory.resolve("AppRun").apply {
            writeText(APP_RUN)
            setExecutable(true, false)
        }
        appDirectory.resolve("$ICON_NAME.desktop").writeText(
            appImageDesktopEntry(
                name = applicationName.get(),
                comment = comment.get(),
                executable = applicationName.get(),
                icon = ICON_NAME,
                categories = categories.get(),
                className = windowClassName.get(),
                scheme = urlScheme.get(),
            ),
        )
        icon.get().asFile.copyTo(appDirectory.resolve("$ICON_NAME.png"), overwrite = true)
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.delete()
        val runtime = runtimeFile.get()
        val log = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(
                buildList {
                    add(appImageTool.get().ifEmpty { "appimagetool" })
                    if (runtime.isNotEmpty()) addAll(listOf("--runtime-file", runtime))
                    add(appDirectory.absolutePath)
                    add(output.absolutePath)
                },
            )
            // appimagetool is an AppImage itself, and a build machine (a CI runner, a container) may have no FUSE to
            // mount it with.
            environment("APPIMAGE_EXTRACT_AND_RUN", "1")
            environment("ARCH", architecture.get())
            standardOutput = log
            errorOutput = log
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0 || !output.isFile) throw GradleException("appimagetool could not pack ${output.name}:\n$log")
        appDirectory.deleteRecursively()
        logger.lifecycle("Packed $output")
    }

    private companion object {
        const val IMAGE_FOLDER = "Campfire"
        const val ICON_NAME = "campfire"

        /**
         * Starts the jpackage launcher with the arguments the AppImage was given, in the folder it was started from,
         * so that a relative path to a file to open still means what it did. The launcher's configuration names its
         * own folder `$APPDIR`, and the runtime exports a variable of that name for the mount point, so it is unset
         * rather than left for anything to take one for the other.
         */
        val APP_RUN = """
            |#!/bin/sh
            |HERE="${'$'}(dirname "${'$'}(readlink -f "${'$'}0")")"
            |unset APPDIR
            |exec "${'$'}HERE/$IMAGE_FOLDER/bin/Campfire" "${'$'}@"
            |
        """.trimMargin()
    }
}
