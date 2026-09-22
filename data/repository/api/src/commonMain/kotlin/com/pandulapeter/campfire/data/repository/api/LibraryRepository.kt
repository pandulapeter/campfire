/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

/** Questions about the library as files on disk rather than as the songs and setlists read out of them. */
interface LibraryRepository {

    /**
     * How many bytes the song and setlist files add up to, counted by the listing sync works from, so that it covers
     * exactly the files the app considers the library and nothing else the user keeps in the folder. Read from the
     * file system on every call rather than cached, since every write changes it. Throws when the folder cannot be
     * listed.
     */
    suspend fun loadLibrarySize(): Long
}
