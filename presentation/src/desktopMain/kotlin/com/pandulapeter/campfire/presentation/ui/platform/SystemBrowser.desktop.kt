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

import com.pandulapeter.campfire.data.source.remote.api.SystemBrowser
import org.koin.core.annotation.Single
import java.awt.Desktop
import java.net.URI

/**
 * The desktop [SystemBrowser]: the same opener the links in Settings use, offered to the sync authorizer, which is
 * in a module that cannot see this one. A `@Single` found by `PresentationModule`'s component scan, the way
 * `AndroidFilePicker` is.
 */
@Single
internal class DesktopSystemBrowser : SystemBrowser {

    override fun open(url: String) = openUrl(url)
}

/**
 * Opens [url] in the system's browser and answers whether anything took it. `java.awt.Desktop` goes first, and the
 * operating system's own command is what is left - both where AWT has no desktop to speak of, which is a Linux
 * session without the GNOME libraries it looks for, and where it has one that fails, as `browse` does on a machine
 * with no default browser registered.
 */
internal fun openUrl(url: String) = openWithAwt(url) || openWithSystemCommand(url)

/** `getDesktop` throws where `isDesktopSupported` says no, so it is only asked for after that has said yes. */
private fun openWithAwt(url: String) = try {
    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.BROWSE) } else null
    desktop?.browse(URI(url))
    desktop != null
} catch (exception: Exception) {
    println("Could not open $url through java.awt.Desktop: ${exception.message}")
    false
} catch (error: LinkageError) {
    // The desktop peer loads native libraries the first time it is asked for, and one that does not link is an
    // Error rather than an Exception. This runs inside a click handler, where either would close the window.
    println("Could not open $url through java.awt.Desktop: ${error.message}")
    false
}

private fun openWithSystemCommand(url: String) = try {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    val command = when {
        "mac" in osName -> arrayOf("open", url)
        "win" in osName -> arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
        else -> arrayOf("xdg-open", url)
    }
    // Discarded rather than piped: xdg-open may become the browser itself, which then writes its log into a pipe
    // nobody reads and stops once that is full.
    ProcessBuilder(*command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    true
} catch (exception: Exception) {
    println("Could not open $url: ${exception.message}")
    false
}
