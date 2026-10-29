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

import org.gradle.api.GradleException
import java.security.MessageDigest

/** The four-part version of an `.msix` made of [versionName]: the Store keeps the last part for itself. */
fun msixPackageVersion(versionName: String) = (versionName.split('.') + listOf("0", "0")).take(3).joinToString(".") + ".0"

/**
 * The half of the package family name Windows derives from the publisher: the first eight bytes of the SHA-256 of
 * its UTF-16LE text, as thirteen characters of Crockford's base32. Partner Center shows the result on the Product
 * identity page, as "Package/Identity/PublisherId" and as the end of the package family name.
 */
internal fun msixPublisherId(publisher: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(publisher.toByteArray(Charsets.UTF_16LE)).take(8)
    val bits = hash.joinToString("") { (it.toInt() and 0xFF).toString(2).padStart(8, '0') } + "0"
    return bits.chunked(5).map { "0123456789abcdefghjkmnpqrstvwxyz"[it.toInt(2)] }.joinToString("")
}

internal fun xmlEscaped(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/**
 * The lines of a jpackage launcher's configuration with [option] added as the first line of its `[JavaOptions]`
 * section. A configuration without one is not a launcher's, and [missingSection] says what was to be added to it.
 */
internal fun withJavaOption(lines: List<String>, option: String, missingSection: () -> String): List<String> {
    val section = lines.indexOf("[JavaOptions]")
    if (section < 0) throw GradleException(missingSection())
    return lines.take(section + 1) + option + lines.drop(section + 1)
}

internal fun withoutJavaOptionsStartingWith(lines: List<String>, prefix: String) = lines.filterNot { it.startsWith(prefix) }

/** A desktop entry's text with one `StartupWMClass` key, naming [className], as its last line. */
internal fun withStartupWmClass(lines: List<String>, className: String) =
    (lines.filterNot { it.startsWith("StartupWMClass=") } + "StartupWMClass=$className").joinToString(separator = "\n", postfix = "\n")

/**
 * A desktop entry's lines with the launcher registered for [scheme]: its `Exec` ends in `%u`, the field code that
 * passes one address (a file code would make the launcher download it and pass a temporary file instead), and its
 * `MimeType` names `x-scheme-handler/<scheme>`, which is what a desktop looks the scheme's handler up by.
 */
internal fun withUrlScheme(lines: List<String>, scheme: String): List<String> {
    if (lines.none { it.startsWith("Exec=") }) throw GradleException("The desktop entry has no Exec line to add %u to.")
    val handler = "x-scheme-handler/$scheme"
    val withExec = lines.map { line ->
        if (!line.startsWith("Exec=")) return@map line
        line.split(' ').filterNot { it in setOf("%f", "%F", "%u", "%U") }.joinToString(" ") + " %u"
    }
    if (withExec.none { it.startsWith("MimeType=") }) return withExec + "MimeType=$handler;"
    return withExec.map { line ->
        if (!line.startsWith("MimeType=")) return@map line
        val types = line.removePrefix("MimeType=").split(';').filter(String::isNotEmpty)
        "MimeType=" + (types + handler).distinct().joinToString(separator = ";", postfix = ";")
    }
}

/**
 * The desktop entry an AppImage carries at its root, which is all an AppImage has to say how it is launched: the tools
 * that integrate one into the menu (Gear Lever, AppImageLauncher, appimaged) copy it out, point its `Exec` at wherever
 * the file was put and take the icon of the same name beside it. It says what the `.deb`'s entry says once
 * [withUrlScheme] and [withStartupWmClass] have been through it.
 */
internal fun appImageDesktopEntry(name: String, comment: String, executable: String, icon: String, categories: String, className: String, scheme: String) =
    withStartupWmClass(
        withUrlScheme(
            listOf(
                "[Desktop Entry]",
                "Type=Application",
                "Name=$name",
                "Comment=$comment",
                "Exec=$executable",
                "Icon=$icon",
                "Terminal=false",
                "Categories=$categories",
            ),
            scheme,
        ),
        className,
    )

/** The architecture names an AppImage is made and named for, by the JVM's `os.arch`; null for one it is not made for. */
fun appImageArchitecture(osArch: String) = when (osArch) {
    "amd64", "x86_64" -> "x86_64"
    "aarch64", "arm64" -> "aarch64"
    else -> null
}

/**
 * A package's post-install script that also refreshes the desktop database where the system has the command, after
 * the script has installed the desktop entry: before its closing `exit 0`, or at its end if it has none.
 */
internal fun withDesktopDatabaseUpdate(lines: List<String>): String {
    val command = "command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database -q || true"
    if (command in lines) return lines.joinToString(separator = "\n", postfix = "\n")
    val exit = lines.indexOfLast { it.trim() == "exit 0" }
    val updated = if (exit < 0) lines + command else lines.take(exit) + command + lines.drop(exit)
    return updated.joinToString(separator = "\n", postfix = "\n")
}

/**
 * A package script's lines with every `xdg-desktop-menu` call made only where the command exists. jpackage's scripts
 * call it under `set -e`, and a system can lack it although the package's dependency on xdg-utils is met (a distrobox
 * container): the package would then fail to install, and once installed fail to be removed, over a menu entry the app
 * does not need to run. Where the command exists, a failure of it still fails the script.
 */
internal fun withOptionalDesktopMenu(lines: List<String>) = lines.map { line ->
    val call = line.trim()
    if (call.startsWith("xdg-desktop-menu ")) "if command -v xdg-desktop-menu >/dev/null 2>&1; then $call; fi" else line
}

/** A package's md5sums text with the line of [path] given [hash], every other line as it was. */
internal fun withUpdatedMd5(lines: List<String>, path: String, hash: String) = lines.joinToString(separator = "\n", postfix = "\n") { line ->
    if (line.substringAfter("  ") == path) "$hash  $path" else line
}

/** What orders the Windows SDK's version folders (`10.0.22621.0`) by version rather than by name. */
internal fun newestSdkVersionKey(name: String) = name.split('.').map { it.toIntOrNull() ?: 0 }.fold(0L) { total, part -> total * 100_000 + part }
