/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.library.ExportedContent
import com.pandulapeter.campfire.data.model.library.ExportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.nio.file.Files

@Composable
internal actual fun rememberFileOpener(): FileOpener = DesktopFileOpener

/**
 * `java.awt.Desktop.open`, which asks the system for the file's default application (Launch Services on a Mac, which
 * the App Store build's sandbox allows for a file in its own container and hands the opening app the file through a
 * sandbox extension of its own), else the system's own command, as [openUrl] does for an address: where AWT has no
 * desktop, or one that has no application for the type. The command's answer is waited for, since `open` and `xdg-open`
 * say through it whether anything took the file; Windows' `start` shows its own "how do you want to open this file"
 * question instead, so it always takes it.
 */
private object DesktopFileOpener : FileOpener {

    override suspend fun open(file: ExportedFile) = withContext(Dispatchers.IO) {
        val path = when (val content = file.content) {
            is ExportedContent.TemporaryFile -> File(content.path)
            is ExportedContent.Bytes -> Files.createTempFile("campfire", file.name).toFile().apply { writeBytes(content.bytes) }
        }
        openWithAwt(path) || openWithSystemCommand(path)
    }

    /** `getDesktop` throws where `isDesktopSupported` says no, so it is only asked for after that has said yes. */
    private fun openWithAwt(file: File) = try {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.OPEN) } else null
        desktop?.open(file)
        desktop != null
    } catch (exception: Exception) {
        println("Could not open a file through java.awt.Desktop: ${exception.message}")
        false
    } catch (error: LinkageError) {
        // The desktop peer loads native libraries the first time it is asked for, and one that does not link is an Error.
        println("Could not open a file through java.awt.Desktop: ${error.message}")
        false
    }

    private fun openWithSystemCommand(file: File) = try {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val command = when {
            "mac" in osName || "darwin" in osName -> listOf("open", file.absolutePath)
            "win" in osName -> listOf("cmd", "/c", "start", "", file.absolutePath)
            else -> listOf("xdg-open", file.absolutePath)
        }
        ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor() == 0
    } catch (exception: Exception) {
        println("Could not open a file: ${exception.message}")
        false
    }
}
