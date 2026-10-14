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

import com.pandulapeter.campfire.data.model.domain.ImportedFile

/** Where the files of one import come from. */
internal sealed interface ImportSource {

    /** What the import screen's subtitle names while the source is read; null where there is nothing worth naming. */
    val name: String?

    val isEmpty: Boolean

    /** Files already in hand: picked, dropped, handed over by the system, or the one an [Address] downloaded. */
    data class Files(val files: List<ImportedFile>) : ImportSource {

        override val name get() = files.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }

        override val isEmpty get() = files.isEmpty()
    }

    /** A file somebody shared as a web address, downloaded once as the import starts. */
    data class Address(val url: String) : ImportSource {

        /** The host, which is what says whose the file is; the rest of an address is rarely worth reading. */
        override val name get() = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').takeIf { it.isNotBlank() } ?: url

        override val isEmpty get() = url.isBlank()
    }
}
