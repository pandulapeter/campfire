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

import com.pandulapeter.campfire.data.model.importing.PageLineMarks
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * The `share.json` the iOS share extension writes into each share's folder of its inbox (`app/ios/CLAUDE.md`). Decoded
 * here rather than in the app module so that it is tested on the desktop; the Swift side that writes it has no tests.
 *
 * @param files The shared files, as paths relative to the share's folder.
 * @param texts The shared texts, each a UTF-8 file, as paths relative to the share's folder. Empty where the share holds
 *   a file: a text next to one is its caption.
 * @param marks Where the share is a page Safari read (`PageText.js`), its one text's structure marks, one per line split
 *   at `\n`, as the script wrote them; absent from any other share and from an older extension's, which needs no new
 *   [version] since a manifest without them is read as before.
 */
@Serializable
data class ShareInboxManifest(
    val version: Int,
    val subject: String? = null,
    val files: List<String> = emptyList(),
    val texts: List<String> = emptyList(),
    val marks: JsonArray? = null,
) {

    /**
     * Written by a newer extension than this app knows, which is left in the inbox for the version that does - a
     * downgrade is the only way to get here - rather than read wrong or thrown away.
     */
    val isFromNewerVersion get() = version > VERSION

    /**
     * This manifest once the app has taken the file at [path] out of the share (an archive moved into the import's
     * staging folder), so that a share offered again after the process ended names only what is still there; null where
     * nothing of the share is left, which then goes whole.
     */
    fun withoutFile(path: String) = copy(files = files - path).takeIf { it.files.isNotEmpty() || it.texts.isNotEmpty() }

    /**
     * [marks] decoded as `PageSnapshot` decodes the app's own page read's (keep the two in step): null for a line with
     * nothing printed or one the script could not align, and null for all of them where any is not what it writes.
     */
    fun pageMarks(): List<PageLineMarks?>? = try {
        marks?.let { manifestJson.decodeFromJsonElement<List<SharedLineMarks>>(it).map(SharedLineMarks::toPageLineMarks) }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        const val VERSION = 1
    }
}

/** Null for anything that is not a manifest: only a failing disk produces one, and it is deleted with its share. */
fun decodeShareInboxManifest(json: String): ShareInboxManifest? = try {
    manifestJson.decodeFromString<ShareInboxManifest>(json)
} catch (_: SerializationException) {
    null
}

/** The manifest as the extension writes it, for the app to write one back without a file it took. */
fun encodeShareInboxManifest(manifest: ShareInboxManifest): String = manifestJson.encodeToString(ShareInboxManifest.serializer(), manifest)

/** A field a newer extension adds is not a reason to refuse the share; [ShareInboxManifest.version] is. */
private val manifestJson = Json { ignoreUnknownKeys = true }

/** One line's marks as `PageText.js` writes them: numbers, each flag 0 or 1. */
@Serializable
private data class SharedLineMarks(
    val size: Double? = null,
    val unaligned: Int = 0,
    val link: Int = 0,
    val newBlock: Int = 0,
    val landmark: Int = 0,
    val bold: Int = 0,
    val italic: Int = 0,
    val monospace: Int = 0,
    val heading: Int = 0,
) {
    fun toPageLineMarks() = if (size == null || unaligned != 0) {
        null
    } else {
        PageLineMarks(
            size = size.toFloat(),
            isBold = bold != 0,
            isItalic = italic != 0,
            isMonospace = monospace != 0,
            isHeading = heading != 0,
            isLink = link != 0,
            isNewBlock = newBlock != 0,
            isLandmark = landmark != 0,
        )
    }
}
