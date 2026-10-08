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

/** A package's md5sums text with the line of [path] given [hash], every other line as it was. */
internal fun withUpdatedMd5(lines: List<String>, path: String, hash: String) = lines.joinToString(separator = "\n", postfix = "\n") { line ->
    if (line.substringAfter("  ") == path) "$hash  $path" else line
}

/** What orders the Windows SDK's version folders (`10.0.22621.0`) by version rather than by name. */
internal fun newestSdkVersionKey(name: String) = name.split('.').map { it.toIntOrNull() ?: 0 }.fold(0L) { total, part -> total * 100_000 + part }
