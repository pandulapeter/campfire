/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.Setlist

interface SetlistLocalSource {

    /** Every `*.setlist.json` file in the library. Files that cannot be parsed are skipped, never deleted. */
    suspend fun loadSetlists(): List<Setlist>

    /**
     * Writes an empty setlist under a file name derived from the title, suffixed until it is free, and returns it.
     * File naming is the storage layer's business, so callers only supply what goes inside.
     */
    suspend fun createSetlist(title: String, priority: Int): Setlist

    suspend fun saveSetlist(setlist: Setlist)

    /**
     * Parses an exported `*.setlist.json` document. The result carries the file name it would like to have, derived
     * from its title, which [importSetlist] turns into a free one. Null when the document is not a setlist.
     */
    suspend fun parseSetlist(document: String): Setlist?

    /** Writes [setlist] under a free file name based on the one it carries, and returns it under that name. */
    suspend fun importSetlist(setlist: Setlist): Setlist

    /** The setlist file exactly as it is stored, so that exporting it changes nothing. Null if it is missing. */
    suspend fun loadSetlistDocument(fileName: String): String?

    suspend fun deleteSetlist(fileName: String)
}
