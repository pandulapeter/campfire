/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/** What downloading a file to import from an address came to; only a cancellation is ever thrown instead. */
sealed interface ImportDownload {

    /** The file, under the name the host gave it or the address's path ends in, which the import triages it by. */
    class File(val name: String, val bytes: ByteArray) : ImportDownload

    /** The host answered with more than an import reads, see [ImportLimits.MAX_IMPORT_SIZE]; nothing of it is kept. */
    data object TooLarge : ImportDownload

    /** The address answered that there is nothing there, or with nothing at all; asking again gets the same answer. */
    data object Missing : ImportDownload

    /**
     * The host could not be reached, did not answer in time, was busy, or (in a browser) refused to answer the page at
     * all, which may be different a minute later.
     */
    data object Unreachable : ImportDownload
}
