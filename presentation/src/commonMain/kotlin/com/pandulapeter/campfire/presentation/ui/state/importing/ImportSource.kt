/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.share.SharedLink.Readable as ReadableLink

/** Where the files of one import come from. */
internal sealed interface ImportSource {

    /** What the import screen's subtitle names while the source is read; null where there is nothing worth naming. */
    val name: String?

    /** How many files were picked, which the subtitle counts in place of their names once there is more than one. */
    val fileCount: Int get() = 1

    val isEmpty: Boolean

    /** Files already in hand: picked, dropped, handed over by the system, or the one an [Address] downloaded. */
    data class Files(val files: List<ImportedFile>) : ImportSource {

        override val name get() = files.singleOrNull()?.name

        override val fileCount get() = files.size

        override val isEmpty get() = files.isEmpty()
    }

    /** A file somebody shared as a web address, downloaded once as the import starts. */
    data class Address(val url: String) : ImportSource {

        /** The host, which is what says whose the file is; the rest of an address is rarely worth reading. */
        override val name get() = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').takeIf { it.isNotBlank() } ?: url

        override val isEmpty get() = url.isBlank()
    }

    /**
     * A song or setlist somebody shared as a Campfire link, downloaded from the address the app builds from the link's
     * parts (never from one the link names) as the import starts.
     *
     * @param file What the download brought, once it has: a read that fails after it is tried again on the same bytes.
     */
    data class SharedLink(val link: ReadableLink, val file: ImportedFile? = null) : ImportSource {

        /** The title the item had when it was shared, never the download's file name, which is a random id. */
        override val name get() = link.title.takeIf { it.isNotBlank() }

        override val isEmpty get() = false
    }
}
