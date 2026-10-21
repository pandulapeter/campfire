/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement

/**
 * `shared-links.json` at the root of the cloud folder: the account's list of shared links, which every device connected
 * to it reads and edits. [unknownFields] keep what a later version adds at the top, as each entry's do, so that this
 * version's edit does not drop them; a change that a version cannot safely ignore raises [version] instead.
 */
internal data class SharedLinksDocument(
    val version: Int = SharedLinksFormat.VERSION,
    val links: List<SharedLinksEntry> = emptyList(),
    val unknownFields: Map<String, JsonElement> = emptyMap(),
) {

    /** The entry of the library file [fileName] of [kind], under any case or Unicode form of the name. */
    fun entryFor(kind: SharedLinkKind, fileName: String) = links.firstOrNull {
        it.kind == kind.id && LibraryFiles.isSameLibraryName(it.fileName, fileName)
    }
}

/**
 * One shared item.
 *
 * @param fileId With [rlkey], the parts of the Dropbox link that the Campfire link carries.
 * @param sharedAt `yyyy-MM-dd`, for display only.
 * @param state One of [STATE_OK], [STATE_ORPHANED], and [STATE_TOO_LARGE]; another version's unknown state reads as ok.
 * @param built What the copy in the folder was last built from, absent until a run first builds it.
 * @param copyMissing The copy is gone from the folder, so the link opens nothing for good; [state] is then
 * [STATE_ORPHANED]. A member rather than a state of its own: a version without it reads an unknown state as ok, and
 * would show the dead link as working, while it keeps an unknown member and writes `orphaned` itself. Never cleared,
 * since a copy put back by hand does not bring a deleted file's link back; sharing the item again replaces the entry.
 */
@Serializable
internal data class SharedLinksEntry(
    val shareId: String = "",
    val kind: String = "",
    val fileName: String = "",
    val title: String = "",
    val fileId: String = "",
    val rlkey: String = "",
    val sharedAt: String? = null,
    val state: String = STATE_OK,
    val built: SharedCopyBuild? = null,
    val copyMissing: Boolean = false,
    @Transient val unknownFields: Map<String, JsonElement> = emptyMap(),
) {

    companion object {
        const val STATE_OK = "ok"
        const val STATE_ORPHANED = "orphaned"
        const val STATE_TOO_LARGE = "tooLarge"
    }
}

/**
 * What a shared copy was built from, so that every device can tell without reading anything whether its own copy could
 * differ: the fingerprint of its [inputs], the [build] number of the Campfire that built it, and the copy's content
 * [hash], which is missing where the inputs made a copy too large to share.
 */
@Serializable
internal data class SharedCopyBuild(
    val inputs: String,
    val build: Int,
    val hash: String? = null,
)
