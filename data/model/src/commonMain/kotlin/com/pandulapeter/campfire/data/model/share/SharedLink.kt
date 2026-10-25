/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.share

/**
 * A link to a song or setlist shared from someone's Dropbox, as [ShareLinks.parse] read it. Only [ShareLinks] makes
 * one, so every instance passed the link grammar.
 */
sealed interface SharedLink {

    /** The link format's version, [ShareLinks.VERSION] for a link this version of Campfire can open. */
    val version: Int

    /**
     * A link in the format this version reads.
     *
     * Every part is validated, and [downloadUrl] is built from them rather than taken from the link, so a crafted link
     * cannot point the app at another host. [title] is the title the item had when it was shared, for display only.
     */
    @ConsistentCopyVisibility
    data class Readable internal constructor(
        val kind: SharedLinkKind,
        val shareId: String,
        val fileId: String,
        val rlkey: String,
        val title: String,
    ) : SharedLink {
        override val version get() = ShareLinks.VERSION

        /** The link's part after the `#` of [landingUrl], or after `campfire://s/`. */
        val payload get() = ShareLinks.payload(this)

        /** The shared file's direct download, which answers 403 or 404 rather than a page once the link is gone. */
        val downloadUrl get() = "https://dl.dropboxusercontent.com/scl/fi/$fileId/$shareId.zip?rlkey=$rlkey&dl=1"

        /** The address people are given: the website's landing page, which works with or without Campfire installed. */
        val landingUrl get() = "${ShareLinks.LANDING_PAGE}#$payload"
    }

    /**
     * A link a later version of Campfire made, in a format this one cannot read: the recipient is told to update rather
     * than that the link is broken. Nothing past its version is known.
     */
    @ConsistentCopyVisibility
    data class FromNewerVersion internal constructor(
        override val version: Int,
    ) : SharedLink
}
