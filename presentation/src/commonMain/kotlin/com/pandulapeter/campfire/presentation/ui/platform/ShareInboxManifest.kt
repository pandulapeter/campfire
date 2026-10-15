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

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The `share.json` the iOS share extension writes into each share's folder of its inbox (`app/ios/CLAUDE.md`). Decoded
 * here rather than in the app module so that it is tested on the desktop; the Swift side that writes it has no tests.
 *
 * @param files The shared files, as paths relative to the share's folder.
 * @param texts The shared texts, each a UTF-8 file, as paths relative to the share's folder. Empty where the share holds
 *   a file: a text next to one is its caption.
 */
@Serializable
data class ShareInboxManifest(
    val version: Int,
    val subject: String? = null,
    val files: List<String> = emptyList(),
    val texts: List<String> = emptyList(),
) {

    /**
     * Written by a newer extension than this app knows, which is left in the inbox for the version that does - a
     * downgrade is the only way to get here - rather than read wrong or thrown away.
     */
    val isFromNewerVersion get() = version > VERSION

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

/** A field a newer extension adds is not a reason to refuse the share; [ShareInboxManifest.version] is. */
private val manifestJson = Json { ignoreUnknownKeys = true }
